import { ApiError } from './client';

// 서버 오류 코드를 화면 문구로 바꾸는 표다. 문구마다 사용자가 다음에 할 일을 함께 적는다.
const ERROR_MESSAGES: Record<string, string> = {
  // 공통
  INVALID_INPUT: '입력한 값을 다시 확인해 주세요.',
  AUTHENTICATION_REQUIRED: '로그인이 필요해요. 로그인한 뒤 다시 시도해 주세요.',
  ACCESS_DENIED: '이 작업을 할 권한이 없어요. 이전 화면으로 돌아가 주세요.',
  NOT_FOUND: '찾는 내용이 없거나 더 볼 수 없어요. 목록에서 다시 골라 주세요.',
  TOO_MANY_REQUESTS: '요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.',
  METHOD_NOT_ALLOWED: '지원하지 않는 요청이에요. 화면을 새로고침해 주세요.',
  UNSUPPORTED_MEDIA_TYPE: '지원하지 않는 요청 형식이에요. 화면을 새로고침해 주세요.',
  INTERNAL_ERROR: '서버에 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.',
  IDEMPOTENCY_KEY_REQUIRED: '요청 정보가 빠졌어요. 화면을 새로고침한 뒤 다시 시도해 주세요.',
  IDEMPOTENCY_KEY_REUSED: '이미 처리한 요청과 내용이 달라요. 화면을 새로고침한 뒤 다시 시도해 주세요.',
  IDEMPOTENCY_IN_PROGRESS: '앞선 요청을 처리하고 있어요. 잠시 뒤 결과를 확인해 주세요.',

  // 회원·인증
  MEMBER_EMAIL_DUPLICATED: '이미 가입된 이메일이에요. 로그인하거나 다른 이메일을 써 주세요.',
  MEMBER_NICKNAME_DUPLICATED: '이미 쓰는 닉네임이에요. 다른 닉네임을 정해 주세요.',
  MEMBER_DISPOSABLE_EMAIL: '일회용 이메일은 쓸 수 없어요. 평소 쓰는 이메일을 넣어 주세요.',
  MEMBER_PASSWORD_POLICY: '비밀번호 규칙에 맞지 않아요. 안내를 보고 다시 정해 주세요.',
  MEMBER_NOT_VERIFIED: '이메일 인증을 마쳐야 할 수 있어요. 받은 인증 코드를 먼저 입력해 주세요.',
  EMAIL_CODE_INVALID: '인증 코드가 맞지 않아요. 메일에 적힌 코드를 다시 확인해 주세요.',
  EMAIL_CODE_EXPIRED: '인증 코드가 만료됐어요. 코드를 다시 받아 주세요.',
  EMAIL_CODE_ATTEMPTS_EXCEEDED: '코드를 여러 번 틀려서 이 코드는 더 쓸 수 없어요. 코드를 다시 받아 주세요.',
  EMAIL_ALREADY_VERIFIED: '이미 이메일 인증을 마쳤어요. 바로 이용해 주세요.',
  EMAIL_RESEND_LIMITED: '코드를 너무 자주 요청했어요. 잠시 뒤 다시 받아 주세요.',
  LOGIN_FAILED: '이메일이나 비밀번호가 맞지 않아요. 다시 확인해 주세요.',
  LOGIN_LOCKED: '로그인에 여러 번 실패해서 잠시 막혔어요. 잠시 뒤 다시 시도해 주세요.',
  MEMBER_SUSPENDED: '이용이 정지된 계정이에요. 정지가 풀린 뒤 다시 로그인해 주세요.',
  PASSWORD_RESET_TOKEN_INVALID: '쓸 수 없는 재설정 링크예요. 비밀번호 재설정을 다시 요청해 주세요.',
  PASSWORD_RESET_LIMITED: '재설정을 너무 자주 요청했어요. 잠시 뒤 다시 요청해 주세요.',

  // 신뢰
  IDENTITY_ALREADY_VERIFIED: '이미 본인확인을 마쳤어요. 따로 할 일은 없어요.',
  IDENTITY_CI_DUPLICATED: '다른 계정에서 이미 본인확인을 했어요. 그 계정으로 로그인해 주세요.',
  TRUST_LEVEL_INSUFFICIENT: '신뢰 단계가 부족해요. 내 신뢰 단계에서 필요한 조건을 확인해 주세요.',
  COMPANION_REVIEW_NOT_ELIGIBLE: '이 베이스캠프의 동행 후기를 쓸 수 없어요. 함께 다녀온 멤버만 쓸 수 있어요.',
  COMPANION_REVIEW_DEADLINE_PASSED: '동행 후기 작성 기한이 지났어요. 다음 베이스캠프에서 남겨 주세요.',
  COMPANION_REVIEW_DUPLICATED: '이미 동행 후기를 썼어요. 받은 후기에서 확인해 주세요.',
  COMPANION_REVIEW_IMMUTABLE: '동행 후기는 고치거나 지울 수 없어요. 문제가 있으면 신고해 주세요.',
  REPORT_NOT_ELIGIBLE: '이 회원은 신고할 수 없어요. 함께한 베이스캠프가 있는지 확인해 주세요.',
  REPORT_DUPLICATED: '이미 신고했어요. 처리 결과를 기다려 주세요.',
  REPORT_INVALID_STATE: '이미 처리된 신고예요. 화면을 새로고침해 주세요.',
  SANCTION_INVALID_STATE: '이미 해제됐거나 끝난 제재예요. 화면을 새로고침해 주세요.',

  // 장소·박지·후기
  SPOT_RADIUS_TOO_LARGE: '반경은 50km까지 고를 수 있어요. 반경을 줄여 주세요.',
  BAKJI_ALREADY_CONFIRMED: '이미 이 박지를 확인했어요. 다른 박지도 둘러봐 주세요.',
  BAKJI_ALREADY_REPORTED: '이미 이 박지를 신고했어요. 처리 결과를 기다려 주세요.',
  SPOT_INVALID_STATE: '지금 상태에서는 할 수 없는 작업이에요. 화면을 새로고침해 주세요.',
  SPOT_REVIEW_DUPLICATED: '같은 날 다녀온 후기가 이미 있어요. 방문일을 확인해 주세요.',

  // 베이스캠프
  BASECAMP_OPEN_LIMIT: '모집 중인 베이스캠프는 3개까지 열 수 있어요. 기존 모집을 마친 뒤 열어 주세요.',
  BASECAMP_CAPACITY_INVALID: '정원을 정할 수 있는 범위를 벗어났어요. 정원을 다시 정해 주세요.',
  BASECAMP_SCHEDULE_INVALID: '출발일이나 일정을 쓸 수 없어요. 날짜를 다시 골라 주세요.',
  BASECAMP_WARNING_SPOT: '경고 대상 박지에서는 베이스캠프를 열 수 없어요. 다른 장소를 골라 주세요.',
  BASECAMP_CONDITION_NOT_MET: '합류 조건을 채우지 못했어요. 베이스캠프의 합류 조건을 확인해 주세요.',
  BASECAMP_DATE_CONFLICT: '같은 날짜에 확정된 다른 베이스캠프가 있어요. 다른 날짜를 골라 주세요.',
  BASECAMP_REAPPLY_NOT_ALLOWED: '이 베이스캠프에는 다시 합류 신청할 수 없어요. 다른 베이스캠프를 찾아 주세요.',
  BASECAMP_PENDING_LIMIT: '대기 중인 합류 신청이 너무 많아요. 기존 신청을 정리한 뒤 다시 신청해 주세요.',
  BASECAMP_ALREADY_APPLIED: '이미 합류 신청했거나 멤버예요. 내 베이스캠프에서 확인해 주세요.',
  BASECAMP_FULL: '정원이 모두 찼어요. 다른 베이스캠프를 찾아 주세요.',
  BASECAMP_INVALID_STATE: '지금 상태에서는 할 수 없는 작업이에요. 화면을 새로고침해 주세요.',
  BASECAMP_NOT_ENOUGH_MEMBERS: '멤버가 2명 이상이어야 확정할 수 있어요. 합류 신청을 더 받아 주세요.',
  BASECAMP_LEADER_CANNOT_LEAVE: '캠프 리더는 탈퇴할 수 없어요. 베이스캠프를 취소해 주세요.',

  // 공식 행사
  PROGRAM_NOT_IN_APPLY_PERIOD: '지금은 신청 기간이 아니에요. 신청 기간을 확인해 주세요.',
  PROGRAM_SOLD_OUT: '남은 자리가 없어요. 빈자리 알림을 신청해 주세요.',
  PROGRAM_ALREADY_APPLIED: '이미 신청한 행사예요. 내 신청에서 확인해 주세요.',
  PROGRAM_PAYMENT_EXPIRED: '결제 기한이 지났어요. 남은 자리가 있으면 다시 신청해 주세요.',
  PROGRAM_INVALID_STATE: '지금 상태에서는 할 수 없는 작업이에요. 화면을 새로고침해 주세요.',
  PROGRAM_CANCEL_NOT_ALLOWED: '취소할 수 있는 기한이 지났어요. 운영자에게 문의해 주세요.',
  PROGRAM_CAPACITY_DECREASE: '신청이 시작된 뒤에는 정원을 줄일 수 없어요. 정원을 늘리거나 그대로 두세요.',

  // 커뮤니티
  COMMUNITY_ALREADY_REPORTED: '이미 신고한 글이나 댓글이에요.',
  COMMUNITY_INVALID_STATE: '지금 상태에서는 할 수 없는 작업이에요. 화면을 새로고침해 주세요.',
  IMAGE_UPLOAD_FAILED: '이미지를 올리지 못했어요. 다시 시도해 주세요.',

  // 공공데이터
  SYNC_JOB_ALREADY_RUNNING: '같은 동기화 작업이 이미 돌고 있어요. 끝난 뒤 다시 실행해 주세요.',

  // 서버에 닿지 못한 경우
  NETWORK_ERROR: '네트워크에 연결하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.',
};

const FIELD_LABELS: Record<string, string> = {
  email: '이메일',
  password: '비밀번호',
  nickname: '닉네임',
  code: '인증 코드',
  title: '제목',
  content: '내용',
  imageIds: '이미지',
  parentId: '답글 대상',
  reason: '신고 사유',
};

const UNEXPECTED_ERROR_MESSAGE = '예상하지 못한 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.';

export function errorMessage(code: string): string | undefined {
  return ERROR_MESSAGES[code];
}

// 표에 없는 필드 이름이면 서버가 준 이름을 그대로 돌려준다.
export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}

export function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return UNEXPECTED_ERROR_MESSAGE;
}
