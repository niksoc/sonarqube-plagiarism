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
package org.sonar.ce.task.projectanalysis.measure;

import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.ce.task.projectanalysis.metric.Metric;
import org.sonar.db.measure.MeasureDto;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static org.sonar.ce.task.projectanalysis.measure.Measure.Level.toLevel;

public class MeasureDtoToMeasure {

  public Optional<Measure> toMeasure(@Nullable MeasureDto measureDto, Metric metric) {
    requireNonNull(metric);
    if (measureDto == null) {
      return Optional.empty();
    }
    Double value = measureDto.getValue();
    String data = measureDto.getData();
    switch (metric.getType().getValueType()) {
      case INT:
        return toIntegerMeasure(measureDto, value, data);
      case LONG:
        return toLongMeasure(measureDto, value, data);
      case DOUBLE:
        return toDoubleMeasure(measureDto, value, data);
      case BOOLEAN:
        return toBooleanMeasure(measureDto, value, data);
      case STRING:
        return toStringMeasure(measureDto, data);
      case LEVEL:
        return toLevelMeasure(measureDto, data);
      case NO_VALUE:
        return toNoValueMeasure(measureDto);
      default:
        throw new IllegalArgumentException("Unsupported Measure.ValueType " + metric.getType().getValueType());
    }
  }

  private static Optional<Measure> toIntegerMeasure(MeasureDto measureDto, @Nullable Double value, String data) {
    if (value == null) {
      return toNoValueMeasure(measureDto);
    }
    return of(setCommonProperties(Measure.newMeasureBuilder(), measureDto).create(value.intValue(), data));
  }

  private static Optional<Measure> toLongMeasure(MeasureDto measureDto, @Nullable Double value, String data) {
    if (value == null) {
      return toNoValueMeasure(measureDto);
    }
    return of(setCommonProperties(Measure.newMeasureBuilder(), measureDto).create(value.longValue(), data));
  }

  private static Optional<Measure> toDoubleMeasure(MeasureDto measureDto, @Nullable Double value, String data) {
    if (value == null) {
      return toNoValueMeasure(measureDto);
    }

    return of(setCommonProperties(Measure.newMeasureBuilder(), measureDto)
      .create(value, org.sonar.api.measures.Metric.MAX_DECIMAL_SCALE, data));
  }

  private static Optional<Measure> toBooleanMeasure(MeasureDto measureDto, @Nullable Double value, String data) {
    if (value == null) {
      return toNoValueMeasure(measureDto);
    }
    return of(setCommonProperties(Measure.newMeasureBuilder(), measureDto).create(value == 1.0d, data));
  }

  private static Optional<Measure> toStringMeasure(MeasureDto measureDto, @Nullable String data) {
    if (data == null) {
      return toNoValueMeasure(measureDto);
    }
    return of(setCommonProperties(Measure.newMeasureBuilder(), measureDto).create(data));
  }

  private static Optional<Measure> toLevelMeasure(MeasureDto measureDto, @Nullable String data) {
    if (data == null) {
      return toNoValueMeasure(measureDto);
    }
    Optional<Measure.Level> level = toLevel(data);
    if (!level.isPresent()) {
      return toNoValueMeasure(measureDto);
    }
    return of(setCommonProperties(Measure.newMeasureBuilder(), measureDto).create(level.get()));
  }

  private static Optional<Measure> toNoValueMeasure(MeasureDto measureDto) {
    return of(setCommonProperties(Measure.newMeasureBuilder(), measureDto).createNoValue());
  }

  private static Measure.NewMeasureBuilder setCommonProperties(Measure.NewMeasureBuilder builder, MeasureDto measureDto) {
    if (measureDto.getAlertStatus() != null) {
      Optional<Measure.Level> qualityGateStatus = toLevel(measureDto.getAlertStatus());
      if (qualityGateStatus.isPresent()) {
        builder.setQualityGateStatus(new QualityGateStatus(qualityGateStatus.get(), measureDto.getAlertText()));
      }
    }
    if (hasAnyVariation(measureDto)) {
      builder.setVariation(measureDto.getVariation());
    }
    return builder;
  }

  private static boolean hasAnyVariation(MeasureDto measureDto) {
    return measureDto.getVariation() != null;
  }

}
