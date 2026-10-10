import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { login } from '../../api/auth';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { Me } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Notice } from '../../components/Notice';
import { TextField } from '../../components/TextField';
import { formatKstDateTime } from '../../lib/datetime';
import { AuthLayout } from './AuthLayout';
import { safeNextPath, withNext } from './nextPath';
import { EMAIL_REQUIRED, PASSWORD_REQUIRED } from './requiredMessages';
import { useSession } from './session';
import { useRetryCountdown } from './useRetryCountdown';

type LoginField = 'email' | 'password';

type FormError = { message: string; detail?: string };

const FOOTER_LINK_CLASS =
  'inline-flex min-h-11 items-center font-semibold text-forest underline underline-offset-4 hover:text-forest-strong';

// 이메일 인증을 마치지 않은 회원은 로그인은 되지만 인증 코드 화면부터 거친다.
// 인증 코드 화면에서 로그인하러 왔다면 next가 이미 그 화면이라서 그대로 돌려보낸다.
function destinationAfterLogin(me: Pick<Me, 'status'>, next: string | null): string {
  if (me.status !== 'UNVERIFIED') return next ?? '/map';
  return next?.startsWith('/verify-email') ? next : withNext('/verify-email', next);
}

export function LoginPage() {
  const { search } = useLocation();
  const next = safeNextPath(search);
  const session = useSession();
  const lock = useRetryCountdown();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<LoginField, string>>>({});
  const [formError, setFormError] = useState<FormError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    document.title = '로그인 · 피치맵';
  }, []);

  // 이미 로그인한 회원이 이 주소로 들어오면 갈 곳으로 바로 보낸다.
  useEffect(() => {
    if (session.status === 'authenticated' && session.me && !submitting) {
      navigate(destinationAfterLogin(session.me, next), { replace: true });
    }
  }, [session.status, session.me, next, submitting]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || lock.secondsLeft > 0) return;

    const emptyErrors: Partial<Record<LoginField, string>> = {};
    if (email.trim() === '') emptyErrors.email = EMAIL_REQUIRED;
    if (password === '') emptyErrors.password = PASSWORD_REQUIRED;
    setFieldErrors(emptyErrors);
    setFormError(null);
    if (Object.keys(emptyErrors).length > 0) return;

    setSubmitting(true);
    try {
      const result = await login({ email: email.trim(), password });
      const me = await session.refresh();
      navigate(destinationAfterLogin(me ?? result, next), { replace: true });
    } catch (error) {
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (!(error instanceof ApiError)) {
      setFormError({ message: toUserMessage(error) });
      return;
    }
    if (error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: Partial<Record<LoginField, string>> = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (field === 'email' || field === 'password') errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError({ message: others.join(' ') });
      return;
    }
    if (error.code === 'LOGIN_LOCKED' && error.retryAfterSeconds !== undefined) {
      lock.start(error.retryAfterSeconds);
    }
    const suspendedUntil = error.code === 'MEMBER_SUSPENDED' ? error.body?.suspendedUntil : undefined;
    setFormError({
      message: error.message,
      detail: suspendedUntil ? `${formatKstDateTime(suspendedUntil)}까지 이용이 제한돼요.` : undefined,
    });
  }

  return (
    <AuthLayout
      title="로그인"
      footer={
        <>
          <span>아직 계정이 없나요?</span>
          <Link to={withNext('/signup', next)} className={FOOTER_LINK_CLASS}>
            가입하기
          </Link>
        </>
      }
    >
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField
          label="이메일"
          type="email"
          name="email"
          autoComplete="email"
          autoFocus
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldErrors.email}
        />
        <TextField
          label="비밀번호"
          type="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors.password}
        />
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="로그인하지 못했어요">
              <p>{formError.message}</p>
              {formError.detail && <p className="mt-1">{formError.detail}</p>}
            </Notice>
          </div>
        )}
        <Button
          type="submit"
          fullWidth
          loading={submitting}
          disabled={lock.secondsLeft > 0}
          disabledReason={lock.secondsLeft > 0 ? `${lock.secondsLeft}초 뒤에 다시 시도할 수 있어요` : undefined}
        >
          로그인
        </Button>
      </form>
    </AuthLayout>
  );
}
