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
import { shallow } from 'enzyme';
import * as React from 'react';
import { DefaultInputProps } from '../../../utils';
import Input from '../Input';

const settingValue = {
  key: 'example'
};

const settingDefinition: T.SettingCategoryDefinition = {
  category: 'general',
  fields: [],
  key: 'example',
  options: [],
  subCategory: 'Branches',
  type: 'STRING'
};

it('should render PrimitiveInput', () => {
  const setting = { ...settingValue, definition: settingDefinition };
  const onChange = jest.fn();
  const input = shallowRender({ onChange, setting }).find('PrimitiveInput');
  expect(input.length).toBe(1);
  expect(input.prop('setting')).toBe(setting);
  expect(input.prop('value')).toBe('foo');
  expect(input.prop('onChange')).toBe(onChange);
});

it('should render MultiValueInput', () => {
  const setting = { ...settingValue, definition: { ...settingDefinition, multiValues: true } };
  const onChange = jest.fn();
  const value = ['foo', 'bar'];
  const input = shallowRender({ onChange, setting, value }).find('MultiValueInput');
  expect(input.length).toBe(1);
  expect(input.prop('setting')).toBe(setting);
  expect(input.prop('value')).toBe(value);
  expect(input.prop('onChange')).toBe(onChange);
});

it('should render PropertySetInput', () => {
  const setting: T.Setting = {
    ...settingValue,
    definition: { ...settingDefinition, type: 'PROPERTY_SET' }
  };

  const onChange = jest.fn();
  const value = [{ foo: 'bar' }];
  const input = shallowRender({ onChange, setting, value }).find('PropertySetInput');
  expect(input.length).toBe(1);
  expect(input.prop('setting')).toBe(setting);
  expect(input.prop('value')).toBe(value);
  expect(input.prop('onChange')).toBe(onChange);
});

function shallowRender(props: Partial<DefaultInputProps> = {}) {
  return shallow(
    <Input
      onChange={jest.fn()}
      setting={{ ...settingValue, definition: settingDefinition }}
      value="foo"
      {...props}
    />
  );
}
