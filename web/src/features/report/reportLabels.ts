import type { ReportKind, ReportStatus, ReportType, SanctionStatus, SanctionType } from '../../api/types';
import type { BadgeTone } from '../../components/Badge';

export const REPORT_KIND_LABELS: Record<ReportKind, string> = {
  MEMBER: '회원 신고',
  REVIEW: '후기 신고',
};

export const REPORT_TYPE_LABELS: Record<ReportType, string> = {
  NO_SHOW: '약속 불이행',
  MONEY_REQUEST: '금전 요구',
  HARASSMENT_OR_THREAT: '성희롱·위협',
  OFFENSIVE_BEHAVIOR: '불쾌한 행동',
  FAKE_PROFILE: '허위 프로필',
  ILLEGAL_CAMPING_INDUCEMENT: '불법 야영 유도',
  INAPPROPRIATE_REVIEW: '부적절한 후기',
};

export const REPORT_STATUS_META: Record<ReportStatus, { label: string; tone: BadgeTone }> = {
  RECEIVED: { label: '접수', tone: 'neutral' },
  IN_REVIEW: { label: '검토 중', tone: 'sea' },
  ACTIONED: { label: '조치', tone: 'forest' },
  DISMISSED: { label: '기각', tone: 'neutral' },
};

export const SANCTION_TYPE_LABELS: Record<SanctionType, string> = {
  WARNING: '경고',
  SUSPEND_7D: '7일 정지',
  SUSPEND_30D: '30일 정지',
  PERMANENT: '영구 정지',
  TEMPORARY_72H: '72시간 임시 정지',
};

export const SANCTION_STATUS_LABELS: Record<SanctionStatus, string> = {
  ACTIVE: '적용 중',
  LIFTED: '해제됨',
  EXPIRED: '끝남',
};
