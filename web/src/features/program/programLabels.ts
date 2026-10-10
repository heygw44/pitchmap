import type { ProgramListStatus } from '../../api/programs';
import type { ProgramApplicationStatus, ProgramCancelReason, ProgramStatus } from '../../api/types';
import type { BadgeTone } from '../../components/Badge';

export const PROGRAM_STATUS_META: Record<ProgramStatus, { label: string; tone: BadgeTone }> = {
  UPCOMING: { label: '신청 전', tone: 'sea' },
  OPEN: { label: '신청 중', tone: 'forest' },
  CLOSED: { label: '마감', tone: 'neutral' },
  CANCELED: { label: '행사 취소', tone: 'neutral' },
};

export const APPLICATION_STATUS_META: Record<ProgramApplicationStatus, { label: string; tone: BadgeTone }> = {
  PENDING_PAYMENT: { label: '결제 대기', tone: 'warning' },
  CONFIRMED: { label: '확정', tone: 'sea' },
  CANCELED: { label: '취소', tone: 'neutral' },
  EXPIRED: { label: '만료', tone: 'neutral' },
};

export const CANCEL_REASON_LABELS: Record<ProgramCancelReason, string> = {
  USER: '본인 취소',
  EXPIRED: '결제 기한 만료',
  SANCTIONED: '이용 정지',
  PROGRAM_CANCELED: '행사 취소',
  WITHDRAWN: '탈퇴',
};

export const PROGRAM_FILTERS: ReadonlyArray<{ value: ProgramListStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'UPCOMING', label: '신청 전' },
  { value: 'OPEN', label: '신청 중' },
  { value: 'CLOSED', label: '마감' },
];

export const APPLICATION_FILTERS: ReadonlyArray<{ value: ProgramApplicationStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING_PAYMENT', label: '결제 대기' },
  { value: 'CONFIRMED', label: '확정' },
  { value: 'CANCELED', label: '취소' },
  { value: 'EXPIRED', label: '만료' },
];

// 예: 30,000원. 0원은 "무료"로 적는다.
export function feeText(fee: number): string {
  return fee === 0 ? '무료' : `${fee.toLocaleString('ko-KR')}원`;
}

// 예: 3/20. 남은 자리 / 정원이다.
export function seatsText(remainingSeats: number, capacity: number): string {
  return `${remainingSeats}/${capacity}`;
}

// 남은 시간을 mm:ss로 적는다. 이미 지났으면 00:00이다.
export function countdownText(dueAtIso: string, nowMs: number): string {
  const seconds = Math.max(0, Math.floor((Date.parse(dueAtIso) - nowMs) / 1000));
  const minutes = Math.floor(seconds / 60);
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}
