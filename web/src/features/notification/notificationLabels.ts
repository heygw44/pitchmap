import type { NotificationType } from '../../api/types';

// 서버가 정한 순서 그대로 둔다. 이메일 수신 설정 화면이 이 순서로 보여 준다.
export const NOTIFICATION_TYPES: readonly NotificationType[] = [
  'BASECAMP_APPLIED',
  'BASECAMP_APPROVED',
  'BASECAMP_REJECTED',
  'BASECAMP_KICKED',
  'BASECAMP_CONFIRMED',
  'BASECAMP_CANCELED',
  'BASECAMP_COMPLETED',
  'BASECAMP_MEMBER_CHANGED',
  'MEMBER_REPORT_RESOLVED',
  'SANCTION_CONFIRMED',
];

export const NOTIFICATION_LABELS: Record<NotificationType, string> = {
  BASECAMP_APPLIED: '내 베이스캠프에 합류 신청이 왔을 때',
  BASECAMP_APPROVED: '합류 신청이 승인됐을 때',
  BASECAMP_REJECTED: '합류 신청이 거절됐을 때',
  BASECAMP_KICKED: '베이스캠프에서 강퇴됐을 때',
  BASECAMP_CONFIRMED: '베이스캠프가 확정됐을 때',
  BASECAMP_CANCELED: '베이스캠프가 취소됐을 때',
  BASECAMP_COMPLETED: '베이스캠프가 끝나서 동행 후기를 쓸 수 있을 때',
  BASECAMP_MEMBER_CHANGED: '베이스캠프 멤버가 바뀌었을 때',
  MEMBER_REPORT_RESOLVED: '내가 한 신고의 처리 결과가 나왔을 때',
  SANCTION_CONFIRMED: '내게 제재가 확정됐을 때',
};
