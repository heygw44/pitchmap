import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { fetchMemberProfile } from '../../api/members';
import type { MemberProfile } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Evidence } from '../../components/Evidence';
import { Notice } from '../../components/Notice';
import { PageCard, PageLayout } from '../../components/PageLayout';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { AGE_GROUP_LABELS, companionTagLabel, GENDER_LABELS, trustLevelBadge } from './memberLabels';
import { withNext } from './nextPath';
import { useSession } from './session';

type ProfileResult =
  | { memberId: number; attempt: number; profile: MemberProfile }
  | { memberId: number; attempt: number; error: unknown };

// 다른 회원이 보는 프로필. 서버가 요청자에 맞게 필드를 빼서 주므로, 화면은 응답에 있는 값만 보여 준다.
export function MemberProfilePage({ memberId }: { memberId: number }) {
  const session = useSession();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<ProfileResult | null>(null);

  // 로그인 여부에 따라 서버가 주는 필드가 달라서, 세션이 바뀌면 다시 받는다.
  useEffect(() => {
    if (session.status === 'loading') return;
    const controller = new AbortController();
    fetchMemberProfile(memberId, controller.signal).then(
      (profile) => {
        if (!controller.signal.aborted) setResult({ memberId, attempt, profile });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ memberId, attempt, error });
      },
    );
    return () => controller.abort();
  }, [memberId, attempt, session.status]);

  const current = result && result.memberId === memberId && result.attempt === attempt ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const nickname = current && 'profile' in current ? current.profile.nickname : null;

  useEffect(() => {
    document.title = nickname ? `${nickname} · 피치맵` : '회원 프로필 · 피치맵';
  }, [nickname]);

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? (
      <div className="flex flex-col gap-3 rounded-control border border-contour bg-card p-5">
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-16 w-full" />
      </div>
    ) : null;
  } else if ('profile' in current) {
    body = <ProfileContent profile={current.profile} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = (
      <div className="rounded-control border border-contour bg-card p-5">
        <EmptyState title="회원을 찾을 수 없어요" description="탈퇴했거나 없는 회원이에요." />
      </div>
    );
  } else {
    body = (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="프로필을 불러오지 못했어요">
          {toUserMessage(current.error)}
        </Notice>
        <div>
          <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  const profile = current && 'profile' in current ? current.profile : null;
  const badge = profile && profile.trustLevel !== undefined ? trustLevelBadge(profile.trustLevel) : null;
  const meta = badge ? (
    <div className="flex flex-wrap gap-1">
      <Badge tone={badge.tone} icon={badge.icon}>
        {badge.label}
      </Badge>
    </div>
  ) : undefined;

  return (
    <PageLayout title={nickname ?? '회원 프로필'} meta={meta}>
      {body}
    </PageLayout>
  );
}

function ProfileContent({ profile }: { profile: MemberProfile }) {
  const { pathname } = useLocation();
  const session = useSession();
  const isMe = session.me?.memberId === profile.memberId;

  // 비회원 응답에는 닉네임만 있다. 신뢰 단계가 없으면 비회원 응답으로 본다.
  if (profile.trustLevel === undefined) {
    return (
      <PageCard title="프로필">
        <p className="text-base text-ink-muted">로그인하면 이 회원의 신뢰 단계와 동행 기록을 볼 수 있어요.</p>
        <Link
          to={withNext('/login', pathname)}
          className="inline-flex min-h-11 items-center self-start font-semibold text-forest underline underline-offset-2"
        >
          로그인
        </Link>
      </PageCard>
    );
  }

  const summary = profile.companionReviewSummary;

  return (
    <>
      <PageCard title="기본 정보">
        <Evidence
          items={[
            { label: '연령대', value: verifiedValue(profile.ageGroup ? AGE_GROUP_LABELS[profile.ageGroup] : null, profile.ageGroupVerified) },
            { label: '성별', value: verifiedValue(profile.gender ? GENDER_LABELS[profile.gender] : null, profile.genderVerified) },
          ]}
        />
        <p className="text-sm text-ink-muted">
          본인확인된 값만 베이스캠프의 연령·동성 조건에 쓰여요. 자기 신고 값은 참고만 해 주세요.
        </p>
        {isMe && (
          <Link to="/me" className="inline-flex min-h-11 items-center self-start text-sm font-semibold text-forest">
            내 정보 고치기
          </Link>
        )}
      </PageCard>

      <PageCard title="동행 기록">
        <Evidence
          items={[
            { label: '완료한 동행', value: `${profile.completedCompanions ?? 0}회` },
            {
              label: '"다시 동행하고 싶어요"',
              value: summary?.rejoinRate === null || summary === undefined ? '아직 받은 후기가 없어요' : `${summary.rejoinRate}%`,
            },
          ]}
        />
        {summary && summary.topTags.length > 0 && (
          <div className="flex flex-col gap-2">
            <p className="text-sm text-ink-muted">자주 받은 태그</p>
            <div className="flex flex-wrap gap-1">
              {summary.topTags.map((tag) => (
                <Badge key={tag} tone="forest">
                  {companionTagLabel(tag)}
                </Badge>
              ))}
            </div>
          </div>
        )}
      </PageCard>
    </>
  );
}

// 본인확인된 값인지 자기 신고 값인지 글로 구분한다. 값이 없으면 밝히지 않은 것이다.
function verifiedValue(label: string | null, verified: boolean | undefined): string {
  if (label === null) return '밝히지 않음';
  return `${label} · ${verified ? '본인확인됨' : '자기 신고'}`;
}
