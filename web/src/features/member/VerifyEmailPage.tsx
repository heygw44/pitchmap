import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { resendVerification, verifyEmail } from '../../api/auth';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { AuthLayout } from './AuthLayout';
import { safeNextPath, withNext } from './nextPath';
import { useSession } from './session';
import { useRetryCountdown } from './useRetryCountdown';

// 코드를 다시 받은 뒤 이 시간 동안은 재발송 버튼을 잠근다.
const RESEND_COOLDOWN_SECONDS = 60;

// 이 오류들은 입력한 코드 때문이라서 입력란 아래에 보여 준다.
const CODE_ERROR_CODES: Record<string, true> = {
  EMAIL_CODE_INVALID: true,
  EMAIL_CODE_EXPIRED: true,
  EMAIL_CODE_ATTEMPTS_EXCEEDED: true,
};

type ResendNotice = { tone: 'info' | 'danger'; title: string; message?: string };

export function VerifyEmailPage() {
  const { search } = useLocation();
  const next = safeNextPath(search);
  const session = useSession();
  const showSkeleton = useDelayedFlag(session.status === 'loading');
  const cooldown = useRetryCountdown();

  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState<string | undefined>(undefined);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendNotice, setResendNotice] = useState<ResendNotice | null>(null);

  useEffect(() => {
    document.title = '이메일 인증 · 피치맵';
  }, []);

  // 인증 코드는 로그인한 세션으로 확인하므로, 로그인하지 않았으면 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') {
      navigate(withNext('/login', withNext('/verify-email', next)), { replace: true });
    }
  }, [session.status, next]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const trimmed = code.trim();
    setFormError(null);
    if (trimmed === '') {
      setCodeError('메일로 받은 6자리 코드를 입력해 주세요.');
      return;
    }
    setCodeError(undefined);

    setSubmitting(true);
    try {
      await verifyEmail(trimmed);
      await session.refresh();
      navigate(next ?? '/', { replace: true });
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError && CODE_ERROR_CODES[error.code]) {
        setCodeError(error.message);
      } else if (error instanceof ApiError && error.code === 'EMAIL_ALREADY_VERIFIED') {
        // 다른 기기에서 먼저 인증했다. 세션을 새로 읽으면 "이미 인증을 마쳤어요" 화면으로 바뀐다.
        await session.refresh().catch(() => null);
      } else if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
        setCodeError(error.fieldErrors.map((item) => item.reason).join(' '));
      } else {
        setFormError(toUserMessage(error));
      }
    }
  }

  async function handleResend() {
    if (resending || cooldown.secondsLeft > 0) return;
    setResending(true);
    setResendNotice(null);
    try {
      await resendVerification();
      setResendNotice({ tone: 'info', title: '새 인증 코드를 보냈어요' });
      setCodeError(undefined);
      cooldown.start(RESEND_COOLDOWN_SECONDS);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'EMAIL_RESEND_LIMITED' && error.retryAfterSeconds !== undefined) {
        cooldown.start(error.retryAfterSeconds);
      }
      if (error instanceof ApiError && error.code === 'EMAIL_ALREADY_VERIFIED') {
        await session.refresh().catch(() => null);
      } else {
        setResendNotice({ tone: 'danger', title: '코드를 다시 보내지 못했어요', message: toUserMessage(error) });
      }
    } finally {
      setResending(false);
    }
  }

  const footer = (
    <Button variant="ghost" onClick={() => void session.logout()}>
      로그아웃
    </Button>
  );

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <AuthLayout title="이메일 인증">
        {showSkeleton ? (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-11 w-full" />
            <Skeleton className="h-11 w-full" />
          </div>
        ) : null}
      </AuthLayout>
    );
  }

  if (session.me.status !== 'UNVERIFIED') {
    return (
      <AuthLayout title="이미 인증을 마쳤어요" footer={footer}>
        <p className="text-ink-muted">이메일 인증은 한 번만 하면 돼요.</p>
        <Link
          to={next ?? '/'}
          replace
          className="inline-flex min-h-11 w-full items-center justify-center rounded-control border border-forest bg-forest px-4 font-semibold text-white hover:border-forest-strong hover:bg-forest-strong"
        >
          {next ? '보던 화면으로 가기' : '지도로 가기'}
        </Link>
      </AuthLayout>
    );
  }

  const resendLocked = cooldown.secondsLeft > 0;

  return (
    <AuthLayout
      title="이메일 인증"
      description={
        <p>
          <span className="font-mono break-all text-ink">{session.me.email}</span>로 보낸 6자리 코드를 입력해 주세요.
          코드는 10분 동안 쓸 수 있고, 5번 틀리면 새 코드를 받아야 해요.
        </p>
      }
      footer={footer}
    >
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField
          label="인증 코드"
          type="text"
          name="code"
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          maxLength={6}
          pattern="\d{6}"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          error={codeError}
        />
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="인증하지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <Button type="submit" fullWidth loading={submitting}>
          인증하기
        </Button>
      </form>
      {resendNotice && (
        <div role="status">
          <Notice tone={resendNotice.tone} title={resendNotice.title}>
            {resendNotice.message && <p>{resendNotice.message}</p>}
          </Notice>
        </div>
      )}
      <Button
        variant="secondary"
        fullWidth
        loading={resending}
        disabled={resendLocked}
        disabledReason={resendLocked ? `${cooldown.secondsLeft}초 뒤에 다시 받을 수 있어요` : undefined}
        onClick={() => void handleResend()}
      >
        코드 다시 받기
      </Button>
    </AuthLayout>
  );
}
