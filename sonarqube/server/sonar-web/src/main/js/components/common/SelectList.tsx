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
import classNames from 'classnames';
import * as key from 'keymaster';
import { uniqueId } from 'lodash';
import * as React from 'react';
import SelectListItem from './SelectListItem';

interface Props {
  className?: string;
  items: string[];
  currentItem: string;
  onSelect: (item: string) => void;
}

interface State {
  active: string;
}

export default class SelectList extends React.PureComponent<Props, State> {
  currentKeyScope?: string;
  previousFilter?: (event: any) => void;
  previousKeyScope?: string;

  constructor(props: Props) {
    super(props);
    this.state = {
      active: props.currentItem
    };
  }

  componentDidMount() {
    this.attachShortcuts();
  }

  componentDidUpdate(prevProps: Props) {
    if (
      prevProps.currentItem !== this.props.currentItem &&
      !this.props.items.includes(this.state.active)
    ) {
      this.setState({ active: this.props.currentItem });
    }
  }

  componentWillUnmount() {
    this.detachShortcuts();
  }

  attachShortcuts = () => {
    this.previousKeyScope = key.getScope();
    this.previousFilter = key.filter;
    this.currentKeyScope = uniqueId('key-scope');
    key.setScope(this.currentKeyScope);

    // sometimes there is a *focused* search field next to the SelectList component
    // we need to allow shortcuts in this case, but only for the used keys
    (key as any).filter = (event: KeyboardEvent & { target: HTMLElement }) => {
      const { tagName } = event.target || event.srcElement;
      const isInput = tagName === 'INPUT' || tagName === 'SELECT' || tagName === 'TEXTAREA';
      return [13, 38, 40].includes(event.keyCode) || !isInput;
    };

    key('down', this.currentKeyScope, () => {
      this.setState(this.selectNextElement);
      return false;
    });

    key('up', this.currentKeyScope, () => {
      this.setState(this.selectPreviousElement);
      return false;
    });

    key('return', this.currentKeyScope, () => {
      if (this.state.active != null) {
        this.handleSelect(this.state.active);
      }
      return false;
    });
  };

  detachShortcuts = () => {
    if (this.previousKeyScope) {
      key.setScope(this.previousKeyScope);
    }
    if (this.currentKeyScope) {
      key.deleteScope(this.currentKeyScope);
    }
    (key as any).filter = this.previousFilter;
  };

  handleSelect = (item: string) => {
    this.props.onSelect(item);
  };

  handleHover = (item: string) => {
    this.setState({ active: item });
  };

  selectNextElement = (state: State, props: Props) => {
    const idx = props.items.indexOf(state.active);
    if (idx < 0) {
      return { active: props.items[0] };
    }
    return { active: props.items[(idx + 1) % props.items.length] };
  };

  selectPreviousElement = (state: State, props: Props) => {
    const idx = props.items.indexOf(state.active);
    if (idx <= 0) {
      return { active: props.items[props.items.length - 1] };
    }
    return { active: props.items[idx - 1] };
  };

  renderChild = (child: any) => {
    if (child == null) {
      return null;
    }
    // do not pass extra props to children like `<li className="divider" />`
    if (child.type !== SelectListItem) {
      return child;
    }
    return React.cloneElement(child, {
      active: this.state.active,
      onHover: this.handleHover,
      onSelect: this.handleSelect
    });
  };

  render() {
    const { children } = this.props;
    const hasChildren = React.Children.count(children) > 0;
    return (
      <ul className={classNames('menu', this.props.className)}>
        {hasChildren && React.Children.map(children, this.renderChild)}
        {!hasChildren &&
          this.props.items.map(item => (
            <SelectListItem
              active={this.state.active}
              item={item}
              key={item}
              onHover={this.handleHover}
              onSelect={this.handleSelect}
            />
          ))}
      </ul>
    );
  }
}
