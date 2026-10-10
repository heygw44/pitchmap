import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { updateMe } from '../../api/members';
import { fetchMyTrust } from '../../api/trust';
import type { Me, MyTrust, SelfAgeGroup, SelfGender } from '../../api/types';
import { Link, navigate } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Evidence } from '../../components/Evidence';
import { AsideSection } from '../../components/HubLayout';
import { Notice } from '../../components/Notice';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { AGE_GROUP_LABELS, GENDER_LABELS, trustLevelBadge } from './memberLabels';
import { withNext } from './nextPath';
import { useSession } from './session';

// 자기 신고 값을 지우는 선택지. 서버에는 null로 보낸다.
const NOT_SET = 'NOT_SET';
type AgeChoice = SelfAgeGroup | typeof NOT_SET;
type GenderChoice = SelfGender | typeof NOT_SET;

const AGE_OPTIONS: ReadonlyArray<ChoiceOption<AgeChoice>> = [
  ...(Object.keys(AGE_GROUP_LABELS) as SelfAgeGroup[]).map((value) => ({ value, label: AGE_GROUP_LABELS[value] })),
  { value: NOT_SET, label: '밝히지 않음' },
];

const GENDER_OPTIONS: ReadonlyArray<ChoiceOption<GenderChoice>> = [
  ...(Object.keys(GENDER_LABELS) as SelfGender[]).map((value) => ({ value, label: GENDER_LABELS[value] })),
  { value: NOT_SET, label: '밝히지 않음' },
];

const SHORTCUT_LINK_CLASS = 'inline-flex min-h-11 items-center text-base font-semibold text-forest hover:text-forest-strong';

const UNVERIFIED_REASON = '이메일 인증을 마치면 고칠 수 있어요';

export function MyPage() {
  const session = useSession();
  const showSkeleton = useDelayedFlag(session.status === 'loading');

  useEffect(() => {
    document.title = '내 정보 · 피치맵';
  }, []);

  // 내 정보는 로그인한 세션으로만 볼 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', '/me'), { replace: true });
  }, [session.status]);

  if (session.status !== 'authenticated' || !session.me) {
    return (
      <PageLayout title="내 정보" description="계정과 신뢰 단계, 프로필을 관리해요.">
        {showSkeleton ? <Skeleton className="h-40 w-full" /> : null}
      </PageLayout>
    );
  }

  const me = session.me;
  return (
    <PageLayout title="내 정보" description="계정과 신뢰 단계, 프로필을 관리해요." aside={<ShortcutAside me={me} onLogout={() => void session.logout()} />}>
      <AccountCard me={me} />
      <TrustCard me={me} />
      <SelfReportForm me={me} onSaved={() => void session.refresh().catch(() => null)} />
    </PageLayout>
  );
}

function ShortcutAside({ me, onLogout }: { me: Me; onLogout: () => void }) {
  return (
    <AsideSection title="바로 가기">
      <ul>
        <li>
          <Link to="/me/basecamps" className={SHORTCUT_LINK_CLASS}>
            내 베이스캠프
          </Link>
        </li>
        <li>
          <Link to="/me/program-applications" className={SHORTCUT_LINK_CLASS}>
            내 행사 신청
          </Link>
        </li>
        <li>
          <Link to="/me/companion-reviews" className={SHORTCUT_LINK_CLASS}>
            동행 후기
          </Link>
        </li>
        <li>
          <Link to="/me/notifications" className={SHORTCUT_LINK_CLASS}>
            알림
          </Link>
        </li>
        {me.role === 'ADMIN' && (
          <li>
            <Link to="/admin/reports" className={SHORTCUT_LINK_CLASS}>
              관리자
            </Link>
          </li>
        )}
        <li>
          <Link to={`/members/${me.memberId}`} className={SHORTCUT_LINK_CLASS}>
            다른 회원에게 보이는 내 프로필
          </Link>
        </li>
      </ul>
      <div>
        <button
          type="button"
          onClick={onLogout}
          className="inline-flex min-h-11 items-center text-base font-semibold text-forest hover:underline"
        >
          로그아웃
        </button>
      </div>
    </AsideSection>
  );
}

function AccountCard({ me }: { me: Me }) {
  return (
    <PageCard title="계정">
      <Evidence
        items={[
          { label: '닉네임', value: me.nickname },
          { label: '이메일', value: me.email },
        ]}
      />
      <p className="text-sm text-ink-muted">이메일은 나만 볼 수 있어요. 다른 회원에게는 보이지 않아요.</p>
      {me.status === 'UNVERIFIED' && (
        <Notice tone="warning" title="이메일 인증이 필요해요">
          <p>인증을 마쳐야 후기 쓰기, 박지 제보 같은 쓰기 기능을 쓸 수 있어요.</p>
          <Link to="/verify-email" className="mt-1 inline-flex min-h-11 items-center font-semibold text-forest">
            인증 코드 입력하기
          </Link>
        </Notice>
      )}
    </PageCard>
  );
}

type TrustResult = { key: string; trust: MyTrust } | { key: string; error: string };

function TrustCard({ me }: { me: Me }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<TrustResult | null>(null);
  // 본인확인을 마치면 단계가 바뀌므로, 세션의 신뢰 단계가 바뀔 때도 다시 받는다. 어느 요청의 결과인지 key로 구분한다.
  const key = `${attempt}:${me.trustLevel}:${me.identityVerified}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchMyTrust(controller.signal).then(
      (trust) => {
        if (!controller.signal.aborted) setResult({ key, trust });
      },
      (cause: unknown) => {
        if (!controller.signal.aborted) setResult({ key, error: toUserMessage(cause) });
      },
    );
    return () => controller.abort();
  }, [key]);

  const current = result?.key === key ? result : null;
  const trust = current && 'trust' in current ? current.trust : null;
  const error = current && 'error' in current ? current.error : null;
  const badge = trustLevelBadge(trust?.trustLevel ?? me.trustLevel);
  const next = trust?.nextLevel;

  return (
    <PageCard title="신뢰 단계">
      <div className="flex flex-wrap gap-1">
        <Badge tone={badge.tone} icon={badge.icon}>
          {badge.label}
        </Badge>
      </div>
      {!me.identityVerified && (
        <Notice tone="info" title="본인확인을 하면 베이스캠프를 열 수 있어요">
          <p>본인확인은 성인만 할 수 있고, 출생연도와 성별만 확인해요.</p>
          <Link to="/identity-verification" className="mt-1 inline-flex min-h-11 items-center font-semibold text-forest">
            본인확인 하기
          </Link>
        </Notice>
      )}
      {error && (
        <div className="flex flex-col gap-2">
          <Notice tone="danger" title="신뢰 단계를 불러오지 못했어요">
            <p>{error}</p>
          </Notice>
          <div>
            <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
              다시 불러오기
            </Button>
          </div>
        </div>
      )}
      {next && (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-ink-muted">신뢰 회원(단계 {next.level})이 되려면</p>
          <Evidence
            items={[
              {
                label: '완료한 동행',
                value: `${next.completedCompanions.current ?? 0} / ${next.completedCompanions.required}회`,
              },
              {
                label: '"다시 동행하고 싶어요"',
                value:
                  next.rejoinRate.current === null
                    ? `받은 후기 없음 / ${next.rejoinRate.required}% 이상`
                    : `${next.rejoinRate.current}% / ${next.rejoinRate.required}% 이상`,
              },
              { label: '최근 180일 제재', value: next.noRecentSanction ? '없음' : '있음' },
            ]}
          />
        </div>
      )}
    </PageCard>
  );
}

type FormField = 'nickname' | 'selfAgeGroup' | 'selfGender';
type FormErrors = Partial<Record<FormField, string>>;

function isFormField(field: string): field is FormField {
  return field === 'nickname' || field === 'selfAgeGroup' || field === 'selfGender';
}

// 닉네임과 자기 신고 연령대·성별을 고친다. 서버는 보내지 않은 필드를 그대로 두므로, 바뀐 값만 보낸다.
function SelfReportForm({ me, onSaved }: { me: Me; onSaved: () => void }) {
  const [nickname, setNickname] = useState(me.nickname);
  const [ageGroup, setAgeGroup] = useState<AgeChoice>(me.selfAgeGroup ?? NOT_SET);
  const [gender, setGender] = useState<GenderChoice>(me.selfGender ?? NOT_SET);
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const unverified = me.status === 'UNVERIFIED';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || unverified) return;
    setSaved(false);
    setFormError(null);
    setFieldErrors({});

    const trimmed = nickname.trim();
    if (trimmed === '') {
      setFieldErrors({ nickname: '닉네임을 적어 주세요.' });
      return;
    }
    const request: { nickname?: string; selfAgeGroup?: SelfAgeGroup | null; selfGender?: SelfGender | null } = {};
    if (trimmed !== me.nickname) request.nickname = trimmed;
    const nextAge = ageGroup === NOT_SET ? null : ageGroup;
    if (nextAge !== me.selfAgeGroup) request.selfAgeGroup = nextAge;
    const nextGender = gender === NOT_SET ? null : gender;
    if (nextGender !== me.selfGender) request.selfGender = nextGender;
    if (Object.keys(request).length === 0) {
      setSaved(true);
      return;
    }

    setSubmitting(true);
    try {
      await updateMe(request);
      setSaved(true);
      onSaved();
    } catch (error) {
      handleError(error);
    } finally {
      setSubmitting(false);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: FormErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isFormField(field)) errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    if (error instanceof ApiError && error.code === 'MEMBER_NICKNAME_DUPLICATED') {
      setFieldErrors({ nickname: toUserMessage(error) });
      return;
    }
    setFormError(toUserMessage(error));
  }

  return (
    <PageCard title="프로필 정보">
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">
          연령대와 성별은 스스로 밝히는 값이에요. 다른 회원에게 "자기 신고"로 보이고, 베이스캠프의 연령·동성 조건에는 본인확인 값만 쓰여요.
        </p>
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="저장하지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <TextField
          label="닉네임"
          name="nickname"
          value={nickname}
          maxLength={20}
          disabled={unverified}
          onChange={(event) => setNickname(event.target.value)}
          error={fieldErrors.nickname}
          hint="2~20자. 앞뒤 공백과 연속 공백은 쓸 수 없어요."
        />
        <ChoiceGroup
          legend="연령대"
          name="selfAgeGroup"
          options={AGE_OPTIONS}
          value={ageGroup}
          onChange={setAgeGroup}
          disabled={unverified}
          error={fieldErrors.selfAgeGroup}
        />
        <ChoiceGroup
          legend="성별"
          name="selfGender"
          options={GENDER_OPTIONS}
          value={gender}
          onChange={setGender}
          disabled={unverified}
          error={fieldErrors.selfGender}
        />
        {saved && (
          <p role="status" className="text-sm text-forest-deep">
            저장했어요.
          </p>
        )}
        <div className="flex justify-end">
          <Button
            type="submit"
            loading={submitting}
            disabled={unverified}
            disabledReason={unverified ? UNVERIFIED_REASON : undefined}
          >
            저장
          </Button>
        </div>
      </form>
    </PageCard>
  );
}
