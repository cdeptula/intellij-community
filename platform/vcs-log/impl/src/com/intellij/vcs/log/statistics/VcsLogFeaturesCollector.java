// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;
import static com.intellij.vcs.log.ui.VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP;
import static java.util.Arrays.asList;

public class VcsLogFeaturesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      VcsLogUiImpl ui = projectLog.getMainLogUi();
      if (ui != null) {
        MainVcsLogUiProperties properties = ui.getProperties();

        Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
        usages.add(StatisticsUtilKt.getBooleanUsage("details", properties.get(CommonUiProperties.SHOW_DETAILS)));
        usages.add(StatisticsUtilKt.getBooleanUsage("diffPreview", properties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)));
        usages.add(StatisticsUtilKt.getBooleanUsage("long.edges", properties.get(SHOW_LONG_EDGES)));

        usages.add(StatisticsUtilKt.getEnumUsage("sort", properties.get(BEK_SORT_TYPE)));
        
        if (ui.getColorManager().isMultipleRoots()) {
          usages.add(StatisticsUtilKt.getBooleanUsage("roots", properties.get(CommonUiProperties.SHOW_ROOT_NAMES)));
        }

        usages.add(StatisticsUtilKt.getBooleanUsage("labels.compact", properties.get(COMPACT_REFERENCES_VIEW)));
        usages.add(StatisticsUtilKt.getBooleanUsage("labels.showTagNames", properties.get(SHOW_TAG_NAMES)));

        usages.add(StatisticsUtilKt.getBooleanUsage("textFilter.regex", properties.get(TEXT_FILTER_REGEX)));
        usages.add(StatisticsUtilKt.getBooleanUsage("textFilter.matchCase", properties.get(TEXT_FILTER_MATCH_CASE)));

        for (VcsLogHighlighterFactory factory : LOG_HIGHLIGHTER_FACTORY_EP.getExtensions(project)) {
          if (factory.showMenuItem()) {
            VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(factory.getId());
            usages.add(StatisticsUtilKt.getBooleanUsage("highlighter." + UsageDescriptorKeyValidator.ensureProperKey(factory.getId()),
                                                        properties.exists(property) && properties.get(property)));
          }
        }

        List<String> tabs = projectLog.getTabsManager().getTabs();
        usages.add(StatisticsUtilKt.getCountingUsage("additionalTabs.count", tabs.size(), asList(0, 1, 2, 3, 4, 8)));

        return usages;
      }
    }
    return Collections.emptySet();
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.vcs.log.ui";
  }
}
