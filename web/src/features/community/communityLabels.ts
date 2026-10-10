import type { CommunityCategory, CommunityReportReason } from '../../api/types';
import type { BadgeTone } from '../../components/Badge';
import type { ChoiceOption } from '../../components/ChoiceGroup';

export const CATEGORY_META: Record<CommunityCategory, { label: string; tone: BadgeTone }> = {
  EXPERIENCE: { label: '백패킹 경험', tone: 'forest' },
  GEAR: { label: '장비 소개', tone: 'sea' },
  FREE: { label: '자유', tone: 'neutral' },
};

export const CATEGORY_ORDER: readonly CommunityCategory[] = ['EXPERIENCE', 'GEAR', 'FREE'];

export const CATEGORY_DESCRIPTIONS: Record<CommunityCategory, string> = {
  EXPERIENCE: '다녀온 박지와 그날의 날씨',
  GEAR: '직접 써 본 텐트와 배낭 이야기',
  FREE: '준비하며 궁금한 것',
};

export const CATEGORY_OPTIONS: ReadonlyArray<ChoiceOption<CommunityCategory>> = CATEGORY_ORDER.map((value) => ({
  value,
  label: CATEGORY_META[value].label,
}));

export const REPORT_REASON_OPTIONS: ReadonlyArray<ChoiceOption<CommunityReportReason>> = [
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'ABUSE', label: '욕설·혐오' },
  { value: 'ILLEGAL_CAMPING', label: '불법 야영 유도' },
  { value: 'PRIVACY', label: '개인정보 노출' },
  { value: 'MONEY_SCAM', label: '금전 요구·사기' },
  { value: 'OTHER', label: '기타' },
];

export function isCategory(value: string | null): value is CommunityCategory {
  return value === 'EXPERIENCE' || value === 'GEAR' || value === 'FREE';
}

// 주소의 spotId는 양의 정수만 받는다. 그 밖의 값은 조건이 없는 것으로 본다.
export function parseSpotId(raw: string | null): number | null {
  if (raw === null || !/^\d+$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

export const UNVERIFIED_WRITE_REASON = '이메일 인증을 마치면 쓸 수 있어요';

export const LINK_BUTTON_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-forest bg-forest px-4 text-base font-semibold text-white hover:bg-forest-strong hover:border-forest-strong';
export const SECONDARY_LINK_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep';
