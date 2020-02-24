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
package org.sonar.ce.task.projectanalysis.qualitymodel;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.sonar.api.ce.measure.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.ce.task.projectanalysis.analysis.AnalysisMetadataHolder;
import org.sonar.ce.task.projectanalysis.component.Component;
import org.sonar.ce.task.projectanalysis.component.PathAwareVisitorAdapter;
import org.sonar.ce.task.projectanalysis.formula.counter.RatingValue;
import org.sonar.ce.task.projectanalysis.issue.ComponentIssuesRepository;
import org.sonar.ce.task.projectanalysis.measure.MeasureRepository;
import org.sonar.ce.task.projectanalysis.metric.Metric;
import org.sonar.ce.task.projectanalysis.metric.MetricRepository;
import org.sonar.ce.task.projectanalysis.period.PeriodHolder;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.server.measure.Rating;

import static org.sonar.api.measures.CoreMetrics.NEW_RELIABILITY_RATING_KEY;
import static org.sonar.api.measures.CoreMetrics.NEW_SECURITY_RATING_KEY;
import static org.sonar.api.rule.Severity.BLOCKER;
import static org.sonar.api.rule.Severity.CRITICAL;
import static org.sonar.api.rule.Severity.INFO;
import static org.sonar.api.rule.Severity.MAJOR;
import static org.sonar.api.rule.Severity.MINOR;
import static org.sonar.api.rules.RuleType.BUG;
import static org.sonar.api.rules.RuleType.VULNERABILITY;
import static org.sonar.ce.task.projectanalysis.component.ComponentVisitor.Order.POST_ORDER;
import static org.sonar.ce.task.projectanalysis.component.CrawlerDepthLimit.LEAVES;
import static org.sonar.ce.task.projectanalysis.measure.Measure.newMeasureBuilder;
import static org.sonar.server.measure.Rating.A;
import static org.sonar.server.measure.Rating.B;
import static org.sonar.server.measure.Rating.C;
import static org.sonar.server.measure.Rating.D;
import static org.sonar.server.measure.Rating.E;

/**
 * Compute following measures :
 * {@link CoreMetrics#NEW_RELIABILITY_RATING_KEY}
 * {@link CoreMetrics#NEW_SECURITY_RATING_KEY}
 */
public class NewReliabilityAndSecurityRatingMeasuresVisitor extends PathAwareVisitorAdapter<NewReliabilityAndSecurityRatingMeasuresVisitor.Counter> {

  private static final Map<String, Rating> RATING_BY_SEVERITY = ImmutableMap.of(
    BLOCKER, E,
    CRITICAL, D,
    MAJOR, C,
    MINOR, B,
    INFO, A);

  private final MeasureRepository measureRepository;
  private final ComponentIssuesRepository componentIssuesRepository;
  private final PeriodHolder periodHolder;

  private final Map<String, Metric> metricsByKey;
  private final AnalysisMetadataHolder analysisMetadataHolder;

  public NewReliabilityAndSecurityRatingMeasuresVisitor(MetricRepository metricRepository, MeasureRepository measureRepository,
    ComponentIssuesRepository componentIssuesRepository, PeriodHolder periodHolder, AnalysisMetadataHolder analysisMetadataHolder) {
    super(LEAVES, POST_ORDER, CounterFactory.INSTANCE);
    this.measureRepository = measureRepository;
    this.componentIssuesRepository = componentIssuesRepository;
    this.periodHolder = periodHolder;

    // Output metrics
    this.metricsByKey = ImmutableMap.of(
      NEW_RELIABILITY_RATING_KEY, metricRepository.getByKey(NEW_RELIABILITY_RATING_KEY),
      NEW_SECURITY_RATING_KEY, metricRepository.getByKey(NEW_SECURITY_RATING_KEY));
    this.analysisMetadataHolder = analysisMetadataHolder;
  }

  @Override
  public void visitProject(Component project, Path<Counter> path) {
    computeAndSaveMeasures(project, path);
  }

  @Override
  public void visitDirectory(Component directory, Path<Counter> path) {
    computeAndSaveMeasures(directory, path);
  }

  @Override
  public void visitFile(Component file, Path<Counter> path) {
    computeAndSaveMeasures(file, path);
  }

  private void computeAndSaveMeasures(Component component, Path<Counter> path) {
    if (!periodHolder.hasPeriod() && !analysisMetadataHolder.isPullRequest()) {
      return;
    }
    initRatingsToA(path);
    processIssues(component, path);
    path.current().newRatingValueByMetric.entrySet()
      .stream()
      .filter(entry -> entry.getValue().isSet())
      .forEach(
        entry -> measureRepository.add(
          component,
          metricsByKey.get(entry.getKey()),
          newMeasureBuilder().setVariation(entry.getValue().getValue().getIndex()).createNoValue()));
    addToParent(path);
  }

  private static void initRatingsToA(Path<Counter> path) {
    path.current().newRatingValueByMetric.values().forEach(entry -> entry.increment(A));
  }

  private void processIssues(Component component, Path<Counter> path) {
    componentIssuesRepository.getIssues(component)
      .stream()
      .filter(issue -> issue.resolution() == null)
      .filter(issue -> issue.type().equals(BUG) || issue.type().equals(VULNERABILITY))
      .forEach(issue -> path.current().processIssue(issue, analysisMetadataHolder.isPullRequest(), periodHolder));
  }

  private static void addToParent(Path<Counter> path) {
    if (!path.isRoot()) {
      path.parent().add(path.current());
    }
  }

  static final class Counter {
    private Map<String, RatingValue> newRatingValueByMetric = ImmutableMap.of(
      NEW_RELIABILITY_RATING_KEY, new RatingValue(),
      NEW_SECURITY_RATING_KEY, new RatingValue());

    private Counter() {
      // prevents instantiation
    }

    void add(Counter otherCounter) {
      newRatingValueByMetric.forEach((metric, rating) -> rating.increment(otherCounter.newRatingValueByMetric.get(metric)));
    }

    void processIssue(Issue issue, boolean isPR, PeriodHolder periodHolder) {
      if (isPR || periodHolder.getPeriod().isOnPeriod(((DefaultIssue) issue).creationDate())) {
        Rating rating = RATING_BY_SEVERITY.get(issue.severity());
        if (issue.type().equals(BUG)) {
          newRatingValueByMetric.get(NEW_RELIABILITY_RATING_KEY).increment(rating);
        } else if (issue.type().equals(VULNERABILITY)) {
          newRatingValueByMetric.get(NEW_SECURITY_RATING_KEY).increment(rating);
        }
      }
    }
  }

  private static final class CounterFactory extends SimpleStackElementFactory<NewReliabilityAndSecurityRatingMeasuresVisitor.Counter> {
    public static final CounterFactory INSTANCE = new CounterFactory();

    private CounterFactory() {
      // prevents instantiation
    }

    @Override
    public Counter createForAny(Component component) {
      return new Counter();
    }
  }
}
