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
/* eslint-disable sonarjs/no-duplicate-string */
import {
  QualityGateApplicationStatus,
  QualityGateProjectStatus,
  QualityGateStatus,
  QualityGateStatusCondition,
  QualityGateStatusConditionEnhanced
} from '../../types/quality-gates';
import { mockMeasureEnhanced, mockMetric } from '../testMocks';

export function mockQualityGate(overrides: Partial<T.QualityGate> = {}): T.QualityGate {
  return {
    id: 1,
    name: 'qualitygate',
    ...overrides
  };
}

export function mockQualityGateStatus(
  overrides: Partial<QualityGateStatus> = {}
): QualityGateStatus {
  return {
    ignoredConditions: false,
    failedConditions: [mockQualityGateStatusConditionEnhanced()],
    key: 'foo',
    name: 'Foo',
    status: 'ERROR',
    ...overrides
  };
}

export function mockQualityGateStatusCondition(
  overrides: Partial<QualityGateStatusCondition> = {}
): QualityGateStatusCondition {
  return {
    actual: '10',
    error: '0',
    level: 'ERROR',
    metric: 'foo',
    op: 'GT',
    ...overrides
  };
}

export function mockQualityGateStatusConditionEnhanced(
  overrides: Partial<QualityGateStatusConditionEnhanced> = {}
): QualityGateStatusConditionEnhanced {
  return {
    actual: '10',
    error: '0',
    level: 'ERROR',
    metric: 'foo',
    op: 'GT',
    measure: mockMeasureEnhanced({ ...(overrides.measure || {}) }),
    ...overrides
  };
}

export function mockQualityGateProjectStatus(
  overrides: Partial<QualityGateProjectStatus> = {}
): QualityGateProjectStatus {
  return {
    conditions: [
      {
        actualValue: '0',
        comparator: 'GT',
        errorThreshold: '1.0',
        metricKey: 'new_bugs',
        periodIndex: 1,
        status: 'OK'
      }
    ],
    ignoredConditions: false,
    status: 'OK',
    ...overrides
  };
}

export function mockQualityGateApplicationStatus(
  overrides: Partial<QualityGateApplicationStatus> = {}
): QualityGateApplicationStatus {
  return {
    metrics: [mockMetric(), mockMetric({ name: 'new_bugs', key: 'new_bugs', type: 'INT' })],
    projects: [
      {
        key: 'foo',
        name: 'Foo',
        conditions: [
          {
            comparator: 'GT',
            errorThreshold: '1.0',
            metric: 'coverage',
            status: 'ERROR',
            value: '10'
          },
          {
            comparator: 'GT',
            errorThreshold: '1.0',
            metric: 'new_bugs',
            periodIndex: 1,
            status: 'ERROR',
            value: '5'
          }
        ],
        status: 'ERROR'
      },
      {
        key: 'bar',
        name: 'Bar',
        conditions: [
          {
            comparator: 'GT',
            errorThreshold: '5.0',
            metric: 'new_bugs',
            periodIndex: 1,
            status: 'ERROR',
            value: '15'
          }
        ],
        status: 'ERROR'
      }
    ],
    status: 'ERROR',
    ...overrides
  };
}
