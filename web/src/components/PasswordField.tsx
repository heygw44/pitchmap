import { useState } from 'react';
import type { ComponentProps } from 'react';
import { Icon } from './icons';
import { TextField } from './TextField';

type PasswordFieldProps = Omit<ComponentProps<typeof TextField>, 'type' | 'trailing'>;

// 비밀번호를 눈으로 확인하게 하는 보기 버튼이 붙은 입력란이다. 붙여넣기는 막지 않는다.
export function PasswordField(props: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);

  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          aria-label="비밀번호 보기"
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
          className="flex h-11 w-11 items-center justify-center rounded-control text-ink-muted hover:text-ink"
        >
          <Icon name={visible ? 'eyeOff' : 'eye'} />
        </button>
      }
    />
  );
}
