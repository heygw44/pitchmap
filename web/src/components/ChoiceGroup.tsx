import { useId } from 'react';
import { Icon } from './icons';

export type ChoiceOption<T extends string> = {
  value: T;
  label: string;
};

type ChoiceGroupProps<T extends string> = {
  legend: string;
  name: string;
  options: ReadonlyArray<ChoiceOption<T>>;
  // 아무것도 고르지 않은 상태는 null이다.
  value: T | null;
  onChange: (value: T) => void;
  error?: string;
  hint?: string;
  disabled?: boolean;
};

// Tailwind가 소스 글자를 읽어 클래스를 만들기 때문에 클래스 이름을 이어 붙이지 않고 통째로 적는다.
const OPTION_SELECTED =
  'inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-control border border-forest bg-forest-soft px-3 text-base font-semibold text-ink';
const OPTION_IDLE =
  'inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-control border border-ink-subtle bg-card px-3 text-base text-ink hover:bg-paper-deep';

// 라디오 버튼 묶음이다. 선택 항목이 하나뿐이고 보기가 적을 때 쓴다.
// 기본 선택을 두지 않을 수 있어서, 잘못된 기본값이 서버에 저장되는 일을 막는 데도 쓴다.
export function ChoiceGroup<T extends string>({
  legend,
  name,
  options,
  value,
  onChange,
  error,
  hint,
  disabled = false,
}: ChoiceGroupProps<T>) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : undefined, error ? errorId : undefined].filter(Boolean).join(' ') || undefined;

  return (
    <fieldset aria-describedby={describedBy} className="flex min-w-0 flex-col gap-1">
      <legend className="mb-1 text-sm font-medium text-ink">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <label key={option.value} className={value === option.value ? OPTION_SELECTED : OPTION_IDLE}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              disabled={disabled}
              aria-invalid={error ? true : undefined}
              onChange={() => onChange(option.value)}
              className="size-5 accent-forest"
            />
            {option.label}
          </label>
        ))}
      </div>
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
    </fieldset>
  );
}
