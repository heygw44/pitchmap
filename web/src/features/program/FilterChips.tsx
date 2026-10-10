type FilterChipsProps<T extends string> = {
  label: string;
  options: ReadonlyArray<{ value: T; label: string }>;
  value: T;
  onChange: (value: T) => void;
};

// 목록 위의 상태 필터다. 눌린 칩은 aria-pressed로 알린다.
export function FilterChips<T extends string>({ label, options, value, onChange }: FilterChipsProps<T>) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {options.map((item) => (
        <button
          key={item.value}
          type="button"
          aria-pressed={value === item.value}
          onClick={() => onChange(item.value)}
          className={
            value === item.value
              ? 'inline-flex min-h-11 items-center rounded-control border border-forest bg-forest-soft px-3 text-base font-semibold text-forest-deep'
              : 'inline-flex min-h-11 items-center rounded-control border border-ink-subtle bg-card px-3 text-base text-ink hover:bg-paper-deep'
          }
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
