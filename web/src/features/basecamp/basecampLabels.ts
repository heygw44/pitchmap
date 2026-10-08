import type { BasecampStatus, JoinCondition, JoinUnmetReason } from '../../api/types';
import type { BadgeTone } from '../../components/Badge';

type StatusMeta = { label: string; tone: BadgeTone };

export const BASECAMP_STATUS_META: Record<BasecampStatus, StatusMeta> = {
  RECRUITING: { label: '모집 중', tone: 'forest' },
  CLOSED: { label: '모집 마감', tone: 'neutral' },
  CONFIRMED: { label: '확정', tone: 'sea' },
  COMPLETED: { label: '완료', tone: 'neutral' },
  CANCELED: { label: '취소', tone: 'neutral' },
};

// 모집 중이 아닌 베이스캠프의 상세에서 신청 버튼 자리에 보여 주는 안내다.
export const BASECAMP_STATUS_GUIDE: Record<BasecampStatus, string> = {
  RECRUITING: '',
  CLOSED: '정원이 차서 모집을 마감했어요. 빈자리가 생기면 다시 모집해요.',
  CONFIRMED: '멤버가 확정돼서 더 이상 신청할 수 없어요.',
  COMPLETED: '이미 다녀온 베이스캠프예요.',
  CANCELED: '취소된 베이스캠프예요.',
};

export const AGE_GROUP_STEPS = [20, 30, 40, 50, 60] as const;

export function ageGroupStepLabel(step: number): string {
  return step >= 60 ? '60대 이상' : `${step}대`;
}

// 예: "단계 1 이상 · 30~40대 · 동성만". 조건이 하나도 없으면 "조건 없음"이다.
export function joinConditionSummary(condition: JoinCondition): string {
  const parts: string[] = [];
  if (condition.minTrustLevel !== null) {
    parts.push(`단계 ${condition.minTrustLevel} 이상`);
  }
  const { ageGroupMin, ageGroupMax } = condition;
  if (ageGroupMin !== null && ageGroupMax !== null) {
    if (ageGroupMin === ageGroupMax) {
      parts.push(ageGroupStepLabel(ageGroupMin));
    } else if (ageGroupMax >= 60) {
      parts.push(`${ageGroupMin}대 이상`);
    } else {
      parts.push(`${ageGroupMin}~${ageGroupMax}대`);
    }
  }
  if (condition.sameGenderOnly) {
    parts.push('동성만');
  }
  return parts.length === 0 ? '조건 없음' : parts.join(' · ');
}

type UnmetReasonText = {
  // 사용자가 왜 못 하는지
  reason: string;
  // 다음에 할 일. 없으면 비워 둔다.
  guide?: string;
};

const IDENTITY_VALUE_GUIDE = '연령대와 성별은 본인확인한 값으로만 판단해요.';

const UNMET_REASON_TEXT: Record<JoinUnmetReason, UnmetReasonText> = {
  TRUST_LEVEL: { reason: '필요한 신뢰 단계에 못 미쳐요.' },
  AGE_GROUP: { reason: '연령대 조건에 맞지 않아요.', guide: IDENTITY_VALUE_GUIDE },
  GENDER: { reason: '성별 조건에 맞지 않아요.', guide: IDENTITY_VALUE_GUIDE },
  DATE_CONFLICT: { reason: '같은 날짜에 확정된 다른 베이스캠프가 있어요.' },
  ALREADY_JOINED: { reason: '이미 신청했거나 멤버예요.' },
  REAPPLY_NOT_ALLOWED: { reason: '거절, 탈퇴, 강퇴된 적이 있어서 다시 신청할 수 없어요.' },
};

// 신뢰 단계가 0이면 본인확인이 필요하고, 1 이상인데 모자라면 동행 기록을 더 쌓아야 한다.
// myTrustLevel을 모르면 일반 문구를 쓴다.
export function unmetReasonText(reason: JoinUnmetReason, myTrustLevel: number | null): UnmetReasonText {
  if (reason === 'TRUST_LEVEL' && myTrustLevel !== null) {
    return myTrustLevel === 0
      ? { reason: '합류하려면 본인확인이 필요해요.' }
      : { reason: '필요한 신뢰 단계에 못 미쳐요.', guide: '완료한 동행을 쌓으면 단계가 올라가요.' };
  }
  return UNMET_REASON_TEXT[reason];
}

// 인원 표시. 예: "3/4"
export function headcountText(headcount: number, capacity: number): string {
  return `${headcount}/${capacity}`;
}
