import type { SelfAgeGroup, SelfGender } from '../../api/types';
import type { BadgeTone } from '../../components/Badge';
import type { IconName } from '../../components/iconPaths';

export const AGE_GROUP_LABELS: Record<SelfAgeGroup, string> = {
  TWENTIES: '20대',
  THIRTIES: '30대',
  FORTIES: '40대',
  FIFTIES: '50대',
  SIXTIES_PLUS: '60대 이상',
};

export const GENDER_LABELS: Record<SelfGender, string> = {
  FEMALE: '여성',
  MALE: '남성',
};

// 신뢰 단계 배지. 0은 이메일 인증, 1은 본인확인, 2는 동행 기록까지 쌓인 신뢰 회원이다.
const TRUST_LEVEL_BADGES: ReadonlyArray<{ label: string; tone: BadgeTone; icon?: IconName }> = [
  { label: '이메일 인증', tone: 'neutral' },
  { label: '본인확인', tone: 'sea', icon: 'check' },
  { label: '신뢰 회원', tone: 'forest' },
];

export function trustLevelBadge(level: number): { label: string; tone: BadgeTone; icon?: IconName } {
  return TRUST_LEVEL_BADGES[level] ?? TRUST_LEVEL_BADGES[0]!;
}

// 동행 후기 태그. 서버가 아직 모르는 값을 주면 받은 코드를 그대로 보여 준다.
const COMPANION_TAG_LABELS: Record<string, string> = {
  ON_TIME: '시간 약속',
  LEAVE_NO_TRACE: '흔적 남기지 않기 실천',
  CONSIDERATE: '배려',
  WELL_PREPARED: '준비성',
};

export function companionTagLabel(tag: string): string {
  return COMPANION_TAG_LABELS[tag] ?? tag;
}
