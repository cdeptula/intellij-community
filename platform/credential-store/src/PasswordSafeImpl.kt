// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("PackageDirectoryMismatch")

package com.intellij.ide.passwordSafe.impl

import com.intellij.credentialStore.*
import com.intellij.credentialStore.kdbx.IncorrectMasterPasswordException
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.PasswordStorage
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.runAsync
import java.nio.file.Path
import java.nio.file.Paths

private fun computeProvider(settings: PasswordSafeSettings): CredentialStore {
  if (settings.providerType == ProviderType.MEMORY_ONLY || (ApplicationManager.getApplication()?.isUnitTestMode == true)) {
    return createInMemoryKeePassCredentialStore()
  }

  if (settings.providerType != ProviderType.KEEPASS) {
    createPersistentCredentialStore()?.let {
      return it
    }
  }

  fun showError(message: String) {
    NOTIFICATION_MANAGER.notify(content = "$message\nIn-memory password storage will be used.", action = object: NotificationAction("Open Settings") {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, PasswordSafeConfigurable::class.java)
      }
    })
  }

  try {
    val dbFile = settings.keepassDb?.let { Paths.get(it) } ?: getDefaultKeePassDbFile()
    return KeePassCredentialStore(dbFile, getDefaultMasterPasswordFile())
  }
  catch (e: IncorrectMasterPasswordException) {
    LOG.warn(e)
    showError("Master password of KeePass database is ${if (e.isFileMissed) "not found" else "not correct"} (${settings.keepassDb}).")
  }
  catch (e: Throwable) {
    LOG.error(e)
    showError("Internal error during opening of KeePass database(${settings.keepassDb})")
  }

  settings.providerType = ProviderType.MEMORY_ONLY
  return createInMemoryKeePassCredentialStore()
}

class PasswordSafeImpl @JvmOverloads constructor(val settings: PasswordSafeSettings /* public - backward compatibility */,
                                                 provider: CredentialStore? = null) : PasswordSafe(), SettingsSavingComponent {
  override var isRememberPasswordByDefault: Boolean
    get() = settings.state.isRememberPasswordByDefault
    set(value) {
      settings.state.isRememberPasswordByDefault = value
    }

  private var _currentProvider: Lazy<CredentialStore> = if (provider == null) lazy { computeProvider(settings) } else lazyOf(provider)

  internal val currentProviderIfComputed: CredentialStore?
    get() = if (_currentProvider.isInitialized()) _currentProvider.value else null

  internal var currentProvider: CredentialStore
    get() = _currentProvider.value
    set(value) {
      _currentProvider = lazyOf(value)
    }

  internal fun closeCurrentProvider() {
    _currentProvider = lazy { computeProvider(settings) }
  }

  // it is helper storage to support set password as memory-only (see setPassword memoryOnly flag)
  private val memoryHelperProvider = lazy { createInMemoryKeePassCredentialStore() }

  override val isMemoryOnly: Boolean
    get() = settings.providerType == ProviderType.MEMORY_ONLY

  // SecureRandom (used to generate master password on first save) can be blocking on Linux
  private val saveAlarm = SingleAlarm(Runnable {
    val currentThread = Thread.currentThread()
    ShutDownTracker.getInstance().registerStopperThread(currentThread)
    try {
      (currentProviderIfComputed as? KeePassCredentialStore)?.save()
    }
    finally {
      ShutDownTracker.getInstance().unregisterStopperThread(currentThread)
    }
  }, 0, Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication())

  override fun get(attributes: CredentialAttributes): Credentials? {
    val value = currentProvider.get(attributes)
    if ((value == null || value.password.isNullOrEmpty()) && memoryHelperProvider.isInitialized()) {
      // if password was set as `memoryOnly`
      memoryHelperProvider.value.get(attributes)?.let {
        if (!it.isEmpty()) {
          return it
        }
      }
    }
    return value
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
    currentProvider.set(attributes, credentials)
    if (attributes.isPasswordMemoryOnly && !credentials?.password.isNullOrEmpty()) {
      // we must store because otherwise on get will be no password
      memoryHelperProvider.value.set(attributes.toPasswordStoreable(), credentials)
    }
    else if (memoryHelperProvider.isInitialized()) {
      memoryHelperProvider.value.set(attributes, null)
    }
  }

  override fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean) {
    if (memoryOnly) {
      memoryHelperProvider.value.set(attributes.toPasswordStoreable(), credentials)
      // remove to ensure that on getPassword we will not return some value from default provider
      currentProvider.set(attributes, null)
    }
    else {
      set(attributes, credentials)
    }
  }

  // maybe in the future we will use native async, so, this method added here instead "if need, just use runAsync in your code"
  override fun getAsync(attributes: CredentialAttributes): Promise<Credentials?> = runAsync { get(attributes) }

  override fun save() {
    if ((currentProviderIfComputed as? KeePassCredentialStore ?: return).isNeedToSave()) {
      saveAlarm.request()
    }
  }

  override fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean {
    if (isMemoryOnly || credentials.password.isNullOrEmpty()) {
      return true
    }

    if (!memoryHelperProvider.isInitialized()) {
      return false
    }

    return memoryHelperProvider.value.get(attributes)?.let {
      !it.password.isNullOrEmpty()
    } ?: false
  }

  @Suppress("unused")
  @Deprecated("Do not use it")
  // public - backward compatibility
  val memoryProvider: PasswordStorage
    get() = memoryHelperProvider.value
}

internal fun createPersistentCredentialStore(): PasswordStorage? {
  LOG.runAndLogException {
    for (factory in CredentialStoreFactory.CREDENTIAL_STORE_FACTORY.extensionList) {
      @Suppress("UnnecessaryVariable")
      val store = factory.create() ?: continue
      return store
    }
  }
  return null
}

@TestOnly
fun createKeePassStore(dbFile: Path, masterPasswordFile: Path): PasswordSafe {
  val store = KeePassCredentialStore(dbFile, masterPasswordFile)
  val settings = PasswordSafeSettings()
  settings.loadState(PasswordSafeSettings.PasswordSafeOptions().apply {
    provider = ProviderType.KEEPASS
    keepassDb = store.dbFile.toString()
  })
  return PasswordSafeImpl(settings, store)
}