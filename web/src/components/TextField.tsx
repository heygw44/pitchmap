import { useId } from 'react';
import type { ComponentPropsWithRef } from 'react';
import { Icon } from './icons';

type TextFieldProps = ComponentPropsWithRef<'input'> & {
  label: string;
  error?: string;
  hint?: string;
};

// 입력란 글자는 16px 아래로 내리지 않는다. iOS가 작은 입력란에 포커스하면 화면을 확대하기 때문이다.
const INPUT_BASE =
  'min-h-11 w-full rounded-control border bg-card px-3 text-base text-ink disabled:cursor-not-allowed disabled:bg-paper-deep disabled:text-ink-muted';

export function TextField({
  label,
  error,
  hint,
  id,
  className,
  'aria-describedby': describedBy,
  ...rest
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;

  const describedByIds =
    [describedBy, hint ? hintId : undefined, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <div className={['flex flex-col gap-1', className ?? ''].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className="text-sm font-medium text-ink">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedByIds}
        className={`${INPUT_BASE} ${error ? 'border-danger' : 'border-ink-subtle'}`}
        {...rest}
      />
      {hint && (
        <p id={hintId} className="text-sm text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-sm text-danger">
          <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}
