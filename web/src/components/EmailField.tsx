import { useId } from 'react';
import { CUSTOM_DOMAIN, EMAIL_DOMAINS, splitEmail } from './emailValue';
import type { EmailValue } from './emailValue';
import { Icon } from './icons';
import { SelectField } from './SelectField';
import { TextField } from './TextField';

const DOMAIN_OPTIONS = [
  { value: '', label: '선택' },
  ...EMAIL_DOMAINS.map((domain) => ({ value: domain, label: domain })),
  { value: CUSTOM_DOMAIN, label: '직접 입력' },
];

type EmailFieldProps = {
  value: EmailValue;
  onChange: (next: EmailValue) => void;
  error?: string;
  autoFocus?: boolean;
  onBlur?: () => void;
};

export function EmailField({ value, onChange, error, autoFocus, onBlur }: EmailFieldProps) {
  const errorId = useId();
  const invalidProps = {
    invalid: Boolean(error),
    'aria-describedby': error ? errorId : undefined,
  };

  return (
    <fieldset className="m-0 flex min-w-0 flex-col gap-1 border-0 p-0">
      <legend className="mb-1 text-sm font-medium text-ink">이메일</legend>
      <div className="flex items-start gap-2">
        <TextField
          className="min-w-0 flex-1"
          hideLabel
          label="이메일 아이디"
          type="text"
          name="email"
          inputMode="email"
          autoComplete="email"
          autoCapitalize="none"
          spellCheck={false}
          autoFocus={autoFocus}
          value={value.local}
          onChange={(event) => onChange(splitEmail(event.target.value, value))}
          onBlur={onBlur}
          {...invalidProps}
        />
        <span aria-hidden="true" className="flex min-h-11 items-center text-ink-muted">
          @
        </span>
        <SelectField
          className="min-w-0 flex-1"
          hideLabel
          label="이메일 도메인"
          options={DOMAIN_OPTIONS}
          value={value.domainChoice}
          onChange={(event) => onChange({ ...value, domainChoice: event.target.value })}
          {...invalidProps}
        />
      </div>
      {value.domainChoice === CUSTOM_DOMAIN && (
        <TextField
          hideLabel
          label="직접 입력할 도메인"
          type="text"
          placeholder="example.com"
          autoCapitalize="none"
          spellCheck={false}
          autoComplete="off"
          value={value.customDomain}
          onChange={(event) => onChange({ ...value, customDomain: event.target.value })}
          {...invalidProps}
        />
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-sm text-danger">
          <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </fieldset>
  );
}
