import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { verifyIdentity } from '../../api/trust';
import type { IdentityVerificationResponse, SelfGender } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { AuthLayout } from '../member/AuthLayout';
import { safeNextPath, withNext } from '../member/nextPath';
import { useSession } from '../member/session';

// 서버가 받는 출생연도 하한이다.
const MIN_BIRTH_YEAR = 1900;
const DEMO_KEY_MAX_LENGTH = 100;

const GENDER_OPTIONS: ReadonlyArray<ChoiceOption<SelfGender>> = [
  { value: 'FEMALE', label: '여성' },
  { value: 'MALE', label: '남성' },
];

type FieldName = 'birthYear' | 'gender' | 'demoIdentityKey';
type FieldErrors = Partial<Record<FieldName, string>>;

function isFieldName(field: string): field is FieldName {
  return field === 'birthYear' || field === 'gender' || field === 'demoIdentityKey';
}

const linkButtonClass =
  'inline-flex min-h-11 w-full items-center justify-center rounded-control border border-forest bg-forest px-4 font-semibold text-white hover:border-forest-strong hover:bg-forest-strong';

export function IdentityVerificationPage() {
  const { search } = useLocation();
  const next = safeNextPath(search);
  const session = useSession();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  const [birthYear, setBirthYear] = useState('');
  const [gender, setGender] = useState<SelfGender | null>(null);
  const [demoIdentityKey, setDemoIdentityKey] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<IdentityVerificationResponse | null>(null);

  useEffect(() => {
    document.title = '본인확인 · 피치맵';
  }, []);

  // 본인확인은 로그인한 세션으로 하므로, 로그인하지 않았으면 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') {
      navigate(withNext('/login', withNext('/identity-verification', next)), {
        replace: true,
      });
    }
  }, [session.status, next]);

  function validate(): {
    request: {
      birthYear: number;
      gender: SelfGender;
      demoIdentityKey: string;
    } | null;
    errors: FieldErrors;
  } {
    const errors: FieldErrors = {};
    const year = Number(birthYear.trim());
    const currentYear = new Date().getFullYear();
    if (!/^\d{4}$/.test(birthYear.trim()) || year < MIN_BIRTH_YEAR || year > currentYear) {
      errors.birthYear = `출생연도를 ${MIN_BIRTH_YEAR}년부터 ${currentYear}년 사이의 숫자 4자리로 입력해 주세요.`;
    }
    if (gender === null) {
      errors.gender = '성별을 골라 주세요.';
    }
    const key = demoIdentityKey.trim();
    if (key === '') {
      errors.demoIdentityKey = '시연용 식별 문자열을 입력해 주세요.';
    } else if (key.length > DEMO_KEY_MAX_LENGTH) {
      errors.demoIdentityKey = `${DEMO_KEY_MAX_LENGTH}자 이하로 입력해 주세요.`;
    }
    if (Object.keys(errors).length > 0 || gender === null) return { request: null, errors };
    return {
      request: { birthYear: year, gender, demoIdentityKey: key },
      errors,
    };
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    setFormError(null);
    const { request, errors } = validate();
    setFieldErrors(errors);
    if (request === null) return;

    setSubmitting(true);
    try {
      const response = await verifyIdentity(request);
      setResult(response);
      await session.refresh().catch(() => null);
    } catch (error) {
      handleError(error);
    } finally {
      setSubmitting(false);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: FieldErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isFieldName(field)) errors[field] = errors[field] ? `${errors[field]} ${reason}` : reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    if (error instanceof ApiError && error.code === 'IDENTITY_ALREADY_VERIFIED') {
      // 다른 기기에서 먼저 마쳤다. 세션을 새로 읽으면 "이미 마쳤어요" 화면으로 바뀐다.
      void session.refresh().catch(() => null);
    }
    setFormError(toUserMessage(error));
  }

  const footer = (
    <Button variant="ghost" onClick={() => void session.logout()}>
      로그아웃
    </Button>
  );

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <AuthLayout title="본인확인">
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

  if (session.me.status === 'UNVERIFIED') {
    return (
      <AuthLayout title="이메일 인증이 먼저 필요해요" footer={footer}>
        <p className="text-ink-muted">본인확인은 이메일 인증을 마친 회원만 할 수 있어요.</p>
        <Link to="/verify-email" className={linkButtonClass}>
          이메일 인증하러 가기
        </Link>
      </AuthLayout>
    );
  }

  if (result !== null || session.me.identityVerified) {
    const adult = result ? result.adult : session.me.trustLevel >= 1;
    return (
      <AuthLayout title="본인확인을 마쳤어요" footer={footer}>
        {adult ? (
          <p className="text-ink-muted">
            성인으로 확인돼서 신뢰 단계 1이 됐어요. 베이스캠프를 열거나 합류하려면 이 단계가 필요해요.
          </p>
        ) : (
          <Notice tone="warning" title="성인 기준에 못 미쳐요">
            <p>만 19세가 되는 해의 1월 1일부터 성인으로 봐요. 그때까지는 신뢰 단계가 0이에요.</p>
          </Notice>
        )}
        <Link to={next ?? '/'} replace className={linkButtonClass}>
          {next ? '보던 화면으로 가기' : '지도로 가기'}
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="본인확인"
      description={<p>베이스캠프를 열거나 합류하려면 본인확인이 필요해요.</p>}
      footer={footer}
    >
      <Notice tone="info" title="시연용 본인확인">
        <p>
          실제 본인확인기관과 연동하지 않아요. 입력한 값을 그대로 믿고, 같은 식별 문자열을 쓰면 같은 사람으로 봐요.
          그래서 한 사람이 계정을 둘 이상 만들 수 없는 규칙을 시연할 수 있어요.
        </p>
      </Notice>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField
          label="출생연도"
          type="text"
          name="birthYear"
          inputMode="numeric"
          maxLength={4}
          autoComplete="off"
          value={birthYear}
          onChange={(event) => setBirthYear(event.target.value)}
          error={fieldErrors.birthYear}
        />
        <ChoiceGroup
          legend="성별"
          name="gender"
          options={GENDER_OPTIONS}
          value={gender}
          onChange={setGender}
          error={fieldErrors.gender}
        />
        <TextField
          label="시연용 식별 문자열"
          type="text"
          name="demoIdentityKey"
          autoComplete="off"
          maxLength={DEMO_KEY_MAX_LENGTH}
          hint="아무 문자열이나 정할 수 있어요. 다른 계정에서 같은 문자열을 쓰면 중복으로 막혀요."
          value={demoIdentityKey}
          onChange={(event) => setDemoIdentityKey(event.target.value)}
          error={fieldErrors.demoIdentityKey}
        />
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="본인확인하지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <Button type="submit" fullWidth loading={submitting}>
          본인확인하기
        </Button>
      </form>
    </AuthLayout>
  );
}
