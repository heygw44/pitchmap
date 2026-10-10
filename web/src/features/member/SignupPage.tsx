import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { login, signUp } from '../../api/auth';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmailField } from '../../components/EmailField';
import { composeEmail, CUSTOM_DOMAIN, EMPTY_EMAIL } from '../../components/emailValue';
import type { EmailValue } from '../../components/emailValue';
import { Notice } from '../../components/Notice';
import { PasswordField } from '../../components/PasswordField';
import { TextField } from '../../components/TextField';
import { AuthLayout } from './AuthLayout';
import { safeNextPath, withNext } from './nextPath';
import {
  EMAIL_CUSTOM_DOMAIN_REQUIRED,
  EMAIL_DOMAIN_REQUIRED,
  EMAIL_REQUIRED,
  NICKNAME_REQUIRED,
  PASSWORD_CONFIRM_MISMATCH,
  PASSWORD_CONFIRM_REQUIRED,
  PASSWORD_REQUIRED,
} from './requiredMessages';
import { useSession } from './session';

type SignupField = 'email' | 'password' | 'passwordConfirm' | 'nickname';

const FOOTER_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-forest underline underline-offset-4 hover:text-forest-strong';

// 서버가 특정 입력란 때문에 거절한 오류는 그 입력란 아래에 보여 준다.
const FIELD_BY_CODE: Record<string, SignupField> = {
  MEMBER_EMAIL_DUPLICATED: 'email',
  MEMBER_DISPOSABLE_EMAIL: 'email',
  MEMBER_PASSWORD_POLICY: 'password',
  MEMBER_NICKNAME_DUPLICATED: 'nickname',
};

function isSignupField(field: string): field is SignupField {
  return field === 'email' || field === 'password' || field === 'passwordConfirm' || field === 'nickname';
}

export function SignupPage() {
  const { search } = useLocation();
  const next = safeNextPath(search);
  const session = useSession();

  const [email, setEmail] = useState<EmailValue>(EMPTY_EMAIL);
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<SignupField, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    document.title = '가입 · 피치맵';
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    // 비밀번호·닉네임 규칙은 서버가 검사한다. 화면은 빈 칸만 미리 막는다.
    const emptyErrors: Partial<Record<SignupField, string>> = {};
    if (email.local.trim() === '') emptyErrors.email = EMAIL_REQUIRED;
    else if (email.domainChoice === '') emptyErrors.email = EMAIL_DOMAIN_REQUIRED;
    else if (email.domainChoice === CUSTOM_DOMAIN && email.customDomain.trim() === '') {
      emptyErrors.email = EMAIL_CUSTOM_DOMAIN_REQUIRED;
    }
    if (password === '') emptyErrors.password = PASSWORD_REQUIRED;
    if (passwordConfirm === '') emptyErrors.passwordConfirm = PASSWORD_CONFIRM_REQUIRED;
    else if (passwordConfirm !== password) emptyErrors.passwordConfirm = PASSWORD_CONFIRM_MISMATCH;
    if (nickname === '') emptyErrors.nickname = NICKNAME_REQUIRED;
    setFieldErrors(emptyErrors);
    setFormError(null);
    if (Object.keys(emptyErrors).length > 0) return;

    setSubmitting(true);
    const credentials = { email: composeEmail(email), password };
    try {
      await signUp({ ...credentials, passwordConfirm, nickname });
    } catch (error) {
      setSubmitting(false);
      handleError(error);
      return;
    }

    // 가입하면 바로 로그인해서 인증 코드 화면으로 보낸다. 가입은 됐으므로 로그인에 실패하면 로그인 화면으로 보낸다.
    try {
      await login(credentials);
      await session.refresh();
      navigate(withNext('/verify-email', next), { replace: true });
    } catch {
      navigate(withNext('/login', next), { replace: true });
    }
  }

  // 확인 입력란을 벗어날 때 비밀번호와 다르면 바로 알린다. 입력하는 도중에는 알리지 않는다.
  function handlePasswordConfirmBlur() {
    if (passwordConfirm !== '' && passwordConfirm !== password) {
      setFieldErrors((current) => ({ ...current, passwordConfirm: PASSWORD_CONFIRM_MISMATCH }));
    }
  }

  // 보이던 오류는 두 값이 같아지면 바로 지운다.
  function handlePasswordConfirmChange(value: string) {
    setPasswordConfirm(value);
    if (fieldErrors.passwordConfirm && value === password) {
      setFieldErrors((current) => ({ ...current, passwordConfirm: undefined }));
    }
  }

  function handleError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError(toUserMessage(error));
      return;
    }
    const field = FIELD_BY_CODE[error.code];
    if (field) {
      setFieldErrors({ [field]: error.message });
      return;
    }
    if (error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: Partial<Record<SignupField, string>> = {};
      const others: string[] = [];
      for (const item of error.fieldErrors) {
        if (isSignupField(item.field)) errors[item.field] = item.reason;
        else others.push(item.reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    setFormError(error.message);
  }

  return (
    <AuthLayout
      title="가입"
      description="가입하면 이메일로 인증 코드를 보내요."
      footer={
        <>
          <span>이미 계정이 있나요?</span>
          <Link to={withNext('/login', next)} className={FOOTER_LINK_CLASS}>
            로그인하기
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <EmailField autoFocus value={email} onChange={setEmail} error={fieldErrors.email} />
        <PasswordField
          label="비밀번호"
          name="password"
          autoComplete="new-password"
          hint="10~64자, 영문·숫자·특수문자를 각각 1자 이상. 공백은 쓸 수 없어요."
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors.password}
        />
        <PasswordField
          label="비밀번호 확인"
          name="passwordConfirm"
          autoComplete="new-password"
          value={passwordConfirm}
          onChange={(event) => handlePasswordConfirmChange(event.target.value)}
          onBlur={handlePasswordConfirmBlur}
          error={fieldErrors.passwordConfirm}
        />
        <TextField
          label="닉네임"
          type="text"
          name="nickname"
          autoComplete="nickname"
          hint="2~20자. 앞뒤 공백과 연속 공백은 쓸 수 없어요."
          value={nickname}
          onChange={(event) => setNickname(event.target.value)}
          error={fieldErrors.nickname}
        />
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="가입하지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <Button type="submit" fullWidth loading={submitting}>
          가입하기
        </Button>
      </form>
    </AuthLayout>
  );
}
