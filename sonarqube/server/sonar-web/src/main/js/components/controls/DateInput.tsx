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
import * as classNames from 'classnames';
import * as addMonths from 'date-fns/add_months';
import * as setMonth from 'date-fns/set_month';
import * as setYear from 'date-fns/set_year';
import * as subMonths from 'date-fns/sub_months';
import { range } from 'lodash';
import * as React from 'react';
import { DayModifiers, Modifier, Modifiers } from 'react-day-picker';
import { InjectedIntlProps, injectIntl } from 'react-intl';
import { ButtonIcon, ClearButton } from 'sonar-ui-common/components/controls/buttons';
import OutsideClickHandler from 'sonar-ui-common/components/controls/OutsideClickHandler';
import Select from 'sonar-ui-common/components/controls/Select';
import CalendarIcon from 'sonar-ui-common/components/icons/CalendarIcon';
import ChevronLeftIcon from 'sonar-ui-common/components/icons/ChevronLeftIcon';
import ChevronRightIcon from 'sonar-ui-common/components/icons/ChevronRightIcon';
import { lazyLoad } from 'sonar-ui-common/components/lazyLoad';
import {
  getShortMonthName,
  getShortWeekDayName,
  getWeekDayName
} from 'sonar-ui-common/helpers/l10n';
import './DayPicker.css';
import './styles.css';

const DayPicker = lazyLoad(() => import('react-day-picker'));

interface Props {
  className?: string;
  currentMonth?: Date;
  highlightFrom?: Date;
  highlightTo?: Date;
  inputClassName?: string;
  maxDate?: Date;
  minDate?: Date;
  name?: string;
  onChange: (date: Date | undefined) => void;
  placeholder: string;
  value?: Date;
}

interface State {
  currentMonth: Date;
  open: boolean;
  lastHovered?: Date;
}

type Week = [string, string, string, string, string, string, string];

export default class DateInput extends React.PureComponent<Props, State> {
  input?: HTMLInputElement | null;

  constructor(props: Props) {
    super(props);
    this.state = { currentMonth: props.value || props.currentMonth || new Date(), open: false };
  }

  focus = () => {
    if (this.input) {
      this.input.focus();
    }
    this.openCalendar();
  };

  handleResetClick = () => {
    this.closeCalendar();
    this.props.onChange(undefined);
  };

  openCalendar = () => {
    this.setState({
      currentMonth: this.props.value || this.props.currentMonth || new Date(),
      lastHovered: undefined,
      open: true
    });
  };

  closeCalendar = () => {
    this.setState({ open: false });
  };

  handleDayClick = (day: Date, modifiers: DayModifiers) => {
    if (!modifiers.disabled) {
      this.closeCalendar();
      this.props.onChange(day);
    }
  };

  handleDayMouseEnter = (day: Date, modifiers: DayModifiers) => {
    this.setState({ lastHovered: modifiers.disabled ? undefined : day });
  };

  handleCurrentMonthChange = ({ value }: { value: number }) => {
    this.setState((state: State) => ({ currentMonth: setMonth(state.currentMonth, value) }));
  };

  handleCurrentYearChange = ({ value }: { value: number }) => {
    this.setState(state => ({ currentMonth: setYear(state.currentMonth, value) }));
  };

  handlePreviousMonthClick = () => {
    this.setState(state => ({ currentMonth: subMonths(state.currentMonth, 1) }));
  };

  handleNextMonthClick = () => {
    this.setState(state => ({ currentMonth: addMonths(state.currentMonth, 1) }));
  };

  render() {
    const { highlightFrom, highlightTo, minDate, value } = this.props;
    const { lastHovered } = this.state;

    const after = this.props.maxDate || new Date();

    const months = range(12);
    const years = range(new Date().getFullYear() - 10, new Date().getFullYear() + 1);

    const selectedDays: Modifier[] = value ? [value] : [];
    let modifiers: Partial<Modifiers> | undefined;
    const lastHoveredOrValue = lastHovered || value;

    if (highlightFrom && lastHoveredOrValue) {
      modifiers = { highlighted: { from: highlightFrom, to: lastHoveredOrValue } };
      selectedDays.push(highlightFrom);
    }
    if (highlightTo && lastHoveredOrValue) {
      modifiers = { highlighted: { from: lastHoveredOrValue, to: highlightTo } };
      selectedDays.push(highlightTo);
    }

    const weekdaysLong = range(7).map(getWeekDayName) as Week;
    const weekdaysShort = range(7).map(getShortWeekDayName) as Week;

    return (
      <OutsideClickHandler onClickOutside={this.closeCalendar}>
        <span className={classNames('date-input-control', this.props.className)}>
          <InputWrapper
            className={classNames('date-input-control-input', this.props.inputClassName, {
              'is-filled': this.props.value !== undefined
            })}
            innerRef={node => (this.input = node)}
            name={this.props.name}
            onFocus={this.openCalendar}
            placeholder={this.props.placeholder}
            readOnly={true}
            type="text"
            value={value}
          />
          <CalendarIcon className="date-input-control-icon" fill="" />
          {this.props.value !== undefined && (
            <ClearButton
              className="button-tiny date-input-control-reset"
              iconProps={{ size: 12 }}
              onClick={this.handleResetClick}
            />
          )}
          {this.state.open && (
            <div className="date-input-calendar">
              <nav className="date-input-calendar-nav">
                <ButtonIcon className="button-small" onClick={this.handlePreviousMonthClick}>
                  <ChevronLeftIcon />
                </ButtonIcon>
                <div className="date-input-calender-month">
                  <Select
                    className="date-input-calender-month-select"
                    onChange={this.handleCurrentMonthChange}
                    options={months.map(month => ({
                      label: getShortMonthName(month),
                      value: month
                    }))}
                    value={this.state.currentMonth.getMonth()}
                  />
                  <Select
                    className="date-input-calender-month-select spacer-left"
                    onChange={this.handleCurrentYearChange}
                    options={years.map(year => ({ label: String(year), value: year }))}
                    value={this.state.currentMonth.getFullYear()}
                  />
                </div>
                <ButtonIcon className="button-small" onClick={this.handleNextMonthClick}>
                  <ChevronRightIcon />
                </ButtonIcon>
              </nav>
              <DayPicker
                captionElement={<NullComponent />}
                disabledDays={{ after, before: minDate }}
                firstDayOfWeek={1}
                modifiers={modifiers}
                month={this.state.currentMonth}
                navbarElement={<NullComponent />}
                onDayClick={this.handleDayClick}
                onDayMouseEnter={this.handleDayMouseEnter}
                selectedDays={selectedDays}
                weekdaysLong={weekdaysLong}
                weekdaysShort={weekdaysShort}
              />
            </div>
          )}
        </span>
      </OutsideClickHandler>
    );
  }
}

function NullComponent() {
  return null;
}

type InputWrapperProps = T.Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value'> &
  InjectedIntlProps & {
    innerRef: React.Ref<HTMLInputElement>;
    value: Date | undefined;
  };

const InputWrapper = injectIntl(({ innerRef, intl, value, ...other }: InputWrapperProps) => {
  const formattedValue =
    value && intl.formatDate(value, { year: 'numeric', month: 'short', day: 'numeric' });
  return <input {...other} ref={innerRef} value={formattedValue || ''} />;
});
