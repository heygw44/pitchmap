import { useId } from 'react';
import type { ComponentPropsWithRef } from 'react';
import { Icon } from './icons';

type TextAreaProps = Omit<ComponentPropsWithRef<'textarea'>, 'value'> & {
  label: string;
  // 글자 수를 세려면 값을 알아야 하므로 제어 컴포넌트로만 쓴다.
  value: string;
  error?: string;
  hint?: string;
};

// 입력란 글자는 16px 아래로 내리지 않는다. iOS가 작은 입력란에 포커스하면 화면을 확대하기 때문이다.
const AREA_BASE =
  'min-h-24 w-full resize-y rounded-control border bg-card px-3 py-2 text-base text-ink disabled:cursor-not-allowed disabled:bg-paper-deep disabled:text-ink-muted';

export function TextArea({
  label,
  value,
  error,
  hint,
  id,
  maxLength,
  className,
  'aria-describedby': describedBy,
  ...rest
}: TextAreaProps) {
  const generatedId = useId();
  const areaId = id ?? generatedId;
  const hintId = `${areaId}-hint`;
  const errorId = `${areaId}-error`;

  const describedByIds =
    [describedBy, hint ? hintId : undefined, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <div className={['flex flex-col gap-1', className ?? ''].filter(Boolean).join(' ')}>
      <label htmlFor={areaId} className="text-sm font-medium text-ink">
        {label}
      </label>
      <textarea
        id={areaId}
        value={value}
        maxLength={maxLength}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedByIds}
        className={`${AREA_BASE} ${error ? 'border-danger' : 'border-ink-subtle'}`}
        {...rest}
      />
      <div className="flex items-start justify-between gap-2">
        <div className="flex min-w-0 flex-col gap-1">
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
        {maxLength !== undefined && (
          <p className="ml-auto shrink-0 font-mono text-sm tabular-nums text-ink-muted">
            {value.length}/{maxLength}
          </p>
        )}
      </div>
    </div>
  );
}
