import { useId } from 'react';
import type { ComponentPropsWithRef } from 'react';
import { Icon } from './icons';

type CheckboxProps = Omit<ComponentPropsWithRef<'input'>, 'type'> & {
  label: string;
  error?: string;
  hint?: string;
};

// 라벨 전체가 눌리는 영역이라 터치 높이가 44px 이상이다.
export function Checkbox({ label, error, hint, id, className, 'aria-describedby': describedBy, ...rest }: CheckboxProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;

  const describedByIds =
    [describedBy, hint ? hintId : undefined, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <div className={['flex flex-col gap-1', className ?? ''].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-base text-ink">
        <input
          id={inputId}
          type="checkbox"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedByIds}
          className="size-5 shrink-0 accent-forest"
          {...rest}
        />
        {label}
      </label>
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
