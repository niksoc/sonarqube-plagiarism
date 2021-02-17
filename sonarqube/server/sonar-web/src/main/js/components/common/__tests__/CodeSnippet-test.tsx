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
import { mount } from 'enzyme';
import * as React from 'react';
import CodeSnippet from '../CodeSnippet';

it('renders correctly', () => {
  expect(mount(<CodeSnippet snippet={'foo\nbar'} />)).toMatchSnapshot();
  expect(mount(<CodeSnippet noCopy={true} snippet={'foo\nbar'} />)).toMatchSnapshot();
});

it('renders correctly with array snippet', () => {
  expect(mount(<CodeSnippet snippet={['foo', 'bar']} />)).toMatchSnapshot();
  expect(mount(<CodeSnippet isOneLine={true} snippet={['foo', 'bar']} />)).toMatchSnapshot();
});
