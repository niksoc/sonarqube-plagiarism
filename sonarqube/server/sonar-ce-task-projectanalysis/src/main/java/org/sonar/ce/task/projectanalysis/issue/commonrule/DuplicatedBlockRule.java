/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.ce.task.projectanalysis.issue.commonrule;

import java.util.Optional;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.ce.task.projectanalysis.component.Component;
import org.sonar.ce.task.projectanalysis.measure.Measure;
import org.sonar.ce.task.projectanalysis.measure.MeasureRepository;
import org.sonar.ce.task.projectanalysis.metric.Metric;
import org.sonar.ce.task.projectanalysis.metric.MetricRepository;
import org.sonar.ce.task.projectanalysis.qualityprofile.ActiveRule;
import org.sonar.ce.task.projectanalysis.qualityprofile.ActiveRulesHolder;
import org.sonar.server.rule.CommonRuleKeys;

import static java.lang.String.format;

public class DuplicatedBlockRule extends CommonRule {

  private final MeasureRepository measureRepository;
  private final Metric duplicatedBlocksMetric;

  public DuplicatedBlockRule(ActiveRulesHolder activeRulesHolder, MeasureRepository measureRepository, MetricRepository metricRepository) {
    super(activeRulesHolder, CommonRuleKeys.DUPLICATED_BLOCKS);
    this.measureRepository = measureRepository;
    this.duplicatedBlocksMetric = metricRepository.getByKey(CoreMetrics.DUPLICATED_BLOCKS_KEY);
  }

  @Override
  protected CommonRuleIssue doProcessFile(Component file, ActiveRule activeRule) {
    Optional<Measure> duplicatedBlocksMeasure = measureRepository.getRawMeasure(file, duplicatedBlocksMetric);
    if (duplicatedBlocksMeasure.isPresent() && duplicatedBlocksMeasure.get().getIntValue() > 0) {
      int duplicatedBlocks = duplicatedBlocksMeasure.get().getIntValue();
      String message = format("%d duplicated blocks of code must be removed.", duplicatedBlocks);
      return new CommonRuleIssue(duplicatedBlocks, message);
    }
    return null;
  }
}
