// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.configurationStore.deserializeInto
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterKeyPasswordSafeTest
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.loadElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

internal class MasterPasswordMigrationTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ApplicationRule()
  }

  @Test
  fun emptyPass() {
    val passwordSafe = convertOldDb(getDb("""<State>
      <option name="MASTER_PASSWORD_INFO" value="" />
      <option name="PASSWORDS">
        <array>
          <array>
            <option value="9a9e0048b26176cefb484b17675c09d8d4363acb16a8c2c194ddc160ea7fee1b" />
            <option value="e86d036dc89ed252627ba61b6023ec0b21cbd58fbbacdbe2b530c4a553903dd8" />
            <option value="05e93059f4487f4cc257133e6c54d81f53b7793965d23ca9e4b0a4960edfca33" />
            <option value="41f099697425bce16e5ddd9ab89e03cc630cc4f5fa081af7c4eb934d4718d902" />
          </array>
          <array>
            <option value="c124cdeb9a72bad8d1f3bc8037b45399" />
            <option value="8962f7731a0b30862b4da5c9d9830a2cc5f5fc1648242d888b16d1668c2dd1ad" />
            <option value="116f1b839d1326d6ecd52b78bb050cd1" />
            <option value="6eee9deabcdc913623785094611a0bf0" />
          </array>
        </array>
      </option>
    </State>"""))
    assertThat(passwordSafe).isNotEmpty

    val provider = KeePassCredentialStore(passwordSafe!!)
    @Suppress("DEPRECATION")
    assertThat(provider.getPassword(MasterKeyPasswordSafeTest::class.java, "TEST")).isEqualTo("test")
  }

  @Test
  fun nonEmptyPass() {
    var passwordSafe: Map<CredentialAttributes, Credentials>? = null
    runInEdtAndWait {
      passwordSafe = convertOldDb(getDb("""<State>
        <option name="MASTER_PASSWORD_INFO" value="" />
        <option name="PASSWORDS">
          <array>
            <array>
              <option value="a3e35d4153b3ddff12629573cd3ef001bd852f33bbf7ada6988362f6f72ccab2" />
              <option value="a35725ba0caaf9ea8dc6a6a001bb03ce0307f5ace094264f3e10019076e5bd3b" />
              <option value="a6f02f741d8be093f6f95db718cf4d779a93ff8917e1693eb29072b4f75aade4" />
              <option value="4acfac8ee7e1f0ef8865150707a911d6d1ac6164ff31d09c1ade16c8a4191568" />
            </array>
            <array>
              <option value="b81ff8a25505f9e6db9d398dcab08774" />
              <option value="d8e6e0d1ea8e5b239c4c87583fc263f8" />
              <option value="05396cc1090959a5cde51670c228bb8fbc6eebb9fae479d8360e11e40642f074" />
              <option value="d8e6e0d1ea8e5b239c4c87583fc263f8" />
            </array>
          </array>
        </option>
      </State>"""))
    }
    assertThat(passwordSafe).isNotEmpty
    val provider = KeePassCredentialStore(passwordSafe!!)
    @Suppress("DEPRECATION")
    assertThat(provider.getPassword(MasterKeyPasswordSafeTest::class.java, "TEST")).isEqualTo("test")
  }

  @Suppress("DEPRECATION")
  private fun getDb(data: String): PasswordDatabase {
    val passwordDatabase = PasswordDatabase()
    val state = PasswordDatabase.State()
    loadElement(data).deserializeInto(state)
    passwordDatabase.loadState(state)
    return passwordDatabase
  }
}