// 서버 API의 요청·응답 모양을 옮긴 타입이다.
// 서버가 시각은 ISO-8601 UTC 문자열로, 날짜는 한국 날짜 YYYY-MM-DD 문자열로 준다.

// 회원

export type MemberStatus = 'UNVERIFIED' | 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';

export type MemberRole = 'USER' | 'ADMIN';

export type SelfAgeGroup = 'TWENTIES' | 'THIRTIES' | 'FORTIES' | 'FIFTIES' | 'SIXTIES_PLUS';

export type SelfGender = 'FEMALE' | 'MALE';

// 오류

export interface FieldErrorItem {
  field: string;
  reason: string;
}

export interface ErrorBody {
  code: string;
  message: string;
  // 서버는 추적 ID를 늘 넣지만, 값을 못 구하면 null이 올 수 있어서 이쪽에서는 비워 둔다.
  traceId?: string;
  // 서버는 입력 검증 오류일 때만 이 필드를 넣는다.
  fieldErrors?: FieldErrorItem[];
  // 이용 정지 중 로그인할 때만 온다. 해제 시각이 없는 정지면 서버가 이 필드를 빼고 준다.
  suspendedUntil?: string;
}

// 가입·로그인·내 정보

export interface SignupRequest {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
}

export interface SignupResponse {
  memberId: number;
  status: MemberStatus;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  memberId: number;
  nickname: string;
  status: MemberStatus;
  role: MemberRole;
}

export interface Me {
  memberId: number;
  email: string;
  nickname: string;
  status: MemberStatus;
  role: MemberRole;
  // 회원이 스스로 밝히지 않았으면 null이다.
  selfAgeGroup: SelfAgeGroup | null;
  selfGender: SelfGender | null;
  identityVerified: boolean;
  trustLevel: number;
}

// 본인확인

export interface IdentityVerificationRequest {
  birthYear: number;
  gender: SelfGender;
  // 시연용 식별 문자열이다. 같은 값이면 서버가 같은 사람으로 본다.
  demoIdentityKey: string;
}

export interface IdentityVerificationResponse {
  identityVerified: boolean;
  // 성인 기준에 못 미치면 false이고 신뢰 단계는 0이다.
  adult: boolean;
  trustLevel: number;
}

// 장소

export type SpotType = 'CAMPSITE' | 'FOREST' | 'BAKJI';

export interface SpotMarker {
  spotId: number;
  type: SpotType;
  name: string;
  lat: number;
  lng: number;
  parkWarning: boolean;
  closedNow: boolean;
}

// 영역 안 장소가 너무 많으면 서버가 칸마다 장소 수와 칸 안 장소들의 평균 좌표를 묶어서 준다.
export interface SpotCluster {
  lat: number;
  lng: number;
  count: number;
}

export interface SpotAreaResponse {
  markers: SpotMarker[];
  clusters: SpotCluster[];
}

export interface SpotAreaQuery {
  swLat: number;
  swLng: number;
  neLat: number;
  neLng: number;
  // 카카오맵 확대 레벨(1~14)
  zoom: number;
  // 비우면 서버가 모든 유형을 준다.
  types?: SpotType[];
  hasWater?: boolean;
  hasToilet?: boolean;
  excludeWarning?: boolean;
}

export type SignalLevel = 'NONE' | 'WEAK' | 'GOOD';

export interface BakjiReporter {
  memberId: number;
  // 제보자가 탈퇴했으면 서버가 익명화한 닉네임을 준다.
  nickname: string;
}

export interface BakjiDetail {
  description: string | null;
  hasWater: boolean;
  hasToilet: boolean;
  // 제보자가 통신 상태를 남기지 않았으면 null이다.
  signalLevel: SignalLevel | null;
  confirmationCount: number;
  reporter: BakjiReporter;
}

// 값은 원천 문자열 그대로다. 그래서 수를 숫자로 바꾸거나 쉼표 목록을 나누지 않았고, 원천에 없는 항목은 null이다.
export interface PublicFacilities {
  toiletCount: string | null;
  showerCount: string | null;
  sinkCount: string | null;
  brazier: string | null;
  amenities: string | null;
  amenitiesEtc: string | null;
  nearbyFacilities: string | null;
  nearbyFacilitiesEtc: string | null;
  petPolicy: string | null;
}

export type PublicSpotSource = 'GOCAMPING' | 'FOREST';

export type OperatingStatus = 'OPERATING' | 'TEMPORARILY_CLOSED' | 'PERMANENTLY_CLOSED';

export interface PublicDetail {
  source: PublicSpotSource;
  // 휴양림은 원천에 분류가 없어서 null이다.
  category: string | null;
  // 원천이 시설 정보를 하나도 주지 않았으면 null이다. 휴양림은 늘 null이다.
  facilities: PublicFacilities | null;
  phone: string | null;
  homepage: string | null;
  // 고캠핑은 기준일을 주지 않아서 null이다.
  sourceDate: string | null;
  operatingStatus: OperatingStatus | null;
  closedFrom: string | null;
  closedUntil: string | null;
  closedNow: boolean;
}

// 경고가 아니면(warned가 false) 서버는 warned만 준다. 경고일 때만 나머지 필드가 함께 온다.
export interface ParkWarning {
  warned: boolean;
  areaName?: string;
  source?: string;
  sourceDate?: string;
  notice?: string;
  // 흔적을 남기지 않는 방법 같은, 서버가 정한 안내 문장이다.
  guide?: string;
}

export interface SpotRating {
  // 후기가 하나도 없으면 null이다.
  average: number | null;
  count: number;
}

export interface ExpectedPeople {
  date: string;
  count: number;
}

// 기상청이 값을 주지 않은 항목은 null이다.
export interface ShortTermForecast {
  at: string;
  temperature: number | null;
  precipitationProbability: number | null;
  windSpeed: number | null;
}

// 날짜는 한국 날짜(YYYY-MM-DD)이고, 나머지 값은 기상청이 주지 않았으면 null이다.
export interface MidTermForecast {
  date: string;
  minTemperature: number | null;
  maxTemperature: number | null;
  amSky: string | null;
  pmSky: string | null;
  amPrecipitationProbability: number | null;
  pmPrecipitationProbability: number | null;
}

export interface SunInfo {
  date: string;
  // HH:mm 형식의 한국 시각
  sunrise: string;
  sunset: string;
  civilTwilightEnd: string;
}

export interface Weather {
  source: string;
  shortTerm: ShortTermForecast[];
  midTerm: MidTermForecast[];
  // 천문연 조회만 실패하면 날씨는 주고 sun만 null이다.
  sun: SunInfo | null;
}

export interface SpotDetail {
  spotId: number;
  type: SpotType;
  name: string;
  lat: number;
  lng: number;
  address: string | null;
  // 서버는 유형에 맞는 하나만 채우고 나머지는 null로 준다.
  bakji: BakjiDetail | null;
  publicDetail: PublicDetail | null;
  parkWarning: ParkWarning;
  rating: SpotRating;
  // 작성 시각이 늦은 순서로 최대 3개다.
  recentReviews: SpotReview[];
  expectedPeople: ExpectedPeople[];
  recruitingBasecamps: unknown[];
  // 날씨 연동 전이거나 외부 API가 실패하면 null이다.
  weather: Weather | null;
}

// 박지 제보와 확인·신고

export type GroundType = 'SOIL' | 'GRASS' | 'GRAVEL' | 'SAND' | 'ROCK' | 'DECK';

// 모르는 선택 항목(description, signalLevel, groundType)은 필드를 빼고 보낸다.
export interface BakjiCreateRequest {
  name: string;
  lat: number;
  lng: number;
  description?: string;
  hasWater: boolean;
  hasToilet: boolean;
  signalLevel?: SignalLevel;
  groundType?: GroundType;
}

export interface DuplicateCandidate {
  spotId: number;
  name: string;
  distanceM: number;
}

export interface BakjiSubmissionResponse {
  spotId: number;
  parkWarning: ParkWarning;
  guide: string;
  duplicateCandidates: DuplicateCandidate[];
}

export interface BakjiConfirmationResponse {
  confirmationCount: number;
}

export type BakjiReportReason = 'ILLEGAL_AREA' | 'CLOSED' | 'FALSE_INFO';

export interface BakjiProblemReportRequest {
  reason: BakjiReportReason;
  content?: string;
}

// 목록 공통 형식. 서버는 전체 개수를 주지 않고 다음 페이지가 있는지만 알려 준다.
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

// 번호형 페이지 목록(커뮤니티 글)은 조건에 맞는 전체 개수와 페이지 수를 더 준다.
export interface NumberedPage<T> extends Page<T> {
  totalElements: number;
  totalPages: number;
}

// 장소 후기

export interface SpotReviewAuthor {
  memberId: number;
  nickname: string;
}

export interface SpotReview {
  reviewId: number;
  author: SpotReviewAuthor;
  // 한국 날짜(YYYY-MM-DD)
  visitedDate: string;
  rating: number;
  content: string;
  createdAt: string;
}

export interface SpotReviewCreateRequest {
  visitedDate: string;
  rating: number;
  content: string;
}

export interface SpotReviewUpdateRequest {
  rating: number;
  content: string;
}

// 내 정보 수정. 보내지 않은 필드는 서버가 그대로 두고, 자기 신고 값에 null을 보내면 지운다.
export interface MeUpdateRequest {
  nickname?: string;
  selfAgeGroup?: SelfAgeGroup | null;
  selfGender?: SelfGender | null;
}

// 신뢰 단계

export interface TrustProgress {
  // 받은 동행 후기가 없으면 rejoinRate.current는 null이다.
  current: number | null;
  required: number;
}

export interface NextTrustLevel {
  level: number;
  completedCompanions: TrustProgress;
  rejoinRate: TrustProgress;
  noRecentSanction: boolean;
}

export interface MyTrust {
  trustLevel: number;
  identityVerified: boolean;
  // 단계 2 회원의 응답에는 이 필드가 없다.
  nextLevel?: NextTrustLevel;
}

// 회원 프로필. 비로그인 요청에는 memberId와 nickname만 오고, 나머지 필드는 응답에서 빠진다.
export interface CompanionReviewSummary {
  // 받은 동행 후기가 없으면 null이다.
  rejoinRate: number | null;
  topTags: string[];
}

export interface MemberProfile {
  memberId: number;
  nickname: string;
  ageGroup?: SelfAgeGroup | null;
  ageGroupVerified?: boolean;
  gender?: SelfGender | null;
  genderVerified?: boolean;
  trustLevel?: number;
  completedCompanions?: number;
  companionReviewSummary?: CompanionReviewSummary;
}

// 베이스캠프

export type BasecampStatus = 'RECRUITING' | 'CLOSED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELED';

// 요청자와 이 베이스캠프의 관계. 비로그인 요청자는 늘 NONE이다.
export type BasecampRelation = 'NONE' | 'APPLICANT' | 'MEMBER' | 'LEADER';

// 합류 신청을 못 하는 이유. 서버가 이 순서로 모두 알려 준다.
export type JoinUnmetReason =
  | 'TRUST_LEVEL'
  | 'AGE_GROUP'
  | 'GENDER'
  | 'DATE_CONFLICT'
  | 'ALREADY_JOINED'
  | 'REAPPLY_NOT_ALLOWED';

// 합류 조건. 연령대 범위는 20, 30, 40, 50, 60(60대 이상) 중 하나씩이고, 조건이 없는 항목은 null이다.
export interface JoinCondition {
  minTrustLevel: number | null;
  ageGroupMin: number | null;
  ageGroupMax: number | null;
  sameGenderOnly: boolean;
}

export interface BasecampSpotSummary {
  spotId: number;
  name: string;
  type: SpotType;
  lat: number;
  lng: number;
}

export interface BasecampSearchItem {
  basecampId: number;
  title: string;
  spot: BasecampSpotSummary;
  startDate: string;
  endDate: string;
  capacity: number;
  headcount: number;
  status: BasecampStatus;
  joinCondition: JoinCondition;
  // 로그인한 요청자에게만 온다. 비로그인이면 두 필드가 응답에서 빠진다.
  canApply?: boolean;
  unmetReasons?: JoinUnmetReason[];
}

// 지도 영역으로 찾는다. 날짜는 한국 날짜(YYYY-MM-DD)이고 양 끝을 포함한다.
export interface BasecampSearchQuery {
  swLat: number;
  swLng: number;
  neLat: number;
  neLng: number;
  fromDate?: string;
  toDate?: string;
  hasVacancy?: boolean;
  // 0부터 시작한다.
  page?: number;
  size?: number;
}

export interface BasecampDetailSpot {
  spotId: number;
  name: string;
  type: SpotType;
}

export interface BasecampLeader {
  memberId: number;
  nickname: string;
  // 로그인한 요청자에게만 온다.
  trustLevel?: number;
}

// 비로그인 응답은 memberId, nickname, role만 오고, 나머지 프로필 필드는 응답에서 빠진다.
export interface BasecampMember {
  memberId: number;
  nickname: string;
  role: 'LEADER' | 'MEMBER';
  ageGroup?: SelfAgeGroup | null;
  ageGroupVerified?: boolean;
  gender?: SelfGender | null;
  genderVerified?: boolean;
  trustLevel?: number;
  completedCompanions?: number;
}

export interface BasecampDetail {
  basecampId: number;
  title: string;
  description: string;
  spot: BasecampDetailSpot;
  startDate: string;
  endDate: string;
  capacity: number;
  headcount: number;
  status: BasecampStatus;
  joinCondition: JoinCondition;
  leader: BasecampLeader;
  members: BasecampMember[];
  myRelation: BasecampRelation;
  // 확정된 베이스캠프의 멤버에게만 오고, 등록된 값이 없으면 빠진다.
  contactInfo?: string;
  safetyNotice: string;
  // 로그인한 요청자가 모집 중인 베이스캠프를 볼 때만 온다.
  canApply?: boolean;
  unmetReasons?: JoinUnmetReason[];
}

export interface BasecampOpenRequest {
  spotId: number;
  title: string;
  description: string;
  startDate: string;
  endDate: string;
  capacity: number;
  // 조건이 없으면 보내지 않는다.
  joinCondition?: JoinCondition;
}

export interface BasecampOpenResponse {
  basecampId: number;
  status: BasecampStatus;
}

export interface BasecampApplyResponse {
  applicationId: number;
  status: 'PENDING';
}

// 캠프 리더 관리

export type BasecampApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED' | 'EXPIRED';

// 신청자 프로필은 상세의 멤버 항목과 같은 규칙이라 프로필 필드가 빠질 수 있다.
export interface BasecampApplicant {
  memberId: number;
  nickname: string;
  ageGroup?: SelfAgeGroup | null;
  ageGroupVerified?: boolean;
  gender?: SelfGender | null;
  genderVerified?: boolean;
  trustLevel?: number;
  completedCompanions?: number;
}

export interface BasecampApplicationItem {
  applicationId: number;
  status: BasecampApplicationStatus;
  message: string | null;
  appliedAt: string;
  applicant: BasecampApplicant;
}

// 승인하면 서버가 베이스캠프의 인원과 상태를 돌려준다.
export interface BasecampApproveResponse {
  headcount: number;
  status: BasecampStatus;
}

export interface BasecampRejectResponse {
  applicationId: number;
  status: BasecampApplicationStatus;
}

export interface BasecampStatusResponse {
  basecampId: number;
  status: BasecampStatus;
}

export type KickReason = 'NO_CONTACT' | 'CONDITION_MISMATCH' | 'INAPPROPRIATE_BEHAVIOR' | 'OTHER';

// 내 베이스캠프

export type MyBasecampRelation = 'LEADER' | 'MEMBER' | 'APPLICANT';

export interface MyBasecampsQuery {
  relation?: MyBasecampRelation;
  status?: BasecampStatus;
  page?: number;
  size?: number;
}

export interface MyBasecampItem {
  basecampId: number;
  title: string;
  spot: BasecampDetailSpot;
  startDate: string;
  endDate: string;
  capacity: number;
  headcount: number;
  status: BasecampStatus;
  myRelation: MyBasecampRelation;
}

// 동행 후기

export interface PendingCompanionTarget {
  memberId: number;
  nickname: string;
}

export interface PendingCompanionReview {
  basecampId: number;
  basecampTitle: string;
  completedAt: string;
  deadline: string;
  targets: PendingCompanionTarget[];
}

export interface CompanionReviewCreateRequest {
  revieweeId: number;
  rejoinWanted: boolean;
  tags?: string[];
  comment?: string;
}

export interface RevealedReceivedCompanionReview {
  reviewId: number;
  basecampId: number;
  basecampTitle: string;
  reviewer: { memberId: number; nickname: string };
  rejoinWanted: boolean;
  tags: string[];
  comment: string | null;
  createdAt: string;
  revealed: true;
}

// 블라인드 공개 전에는 서버가 나머지 필드를 응답에서 뺀다.
export interface SealedReceivedCompanionReview {
  basecampId: number;
  revealed: false;
}

export type ReceivedCompanionReview = RevealedReceivedCompanionReview | SealedReceivedCompanionReview;

// 알림

// 서버가 알림 종류를 정한 순서다. 이메일 수신 설정 화면도 이 순서로 보여 준다.
export type NotificationType =
  | 'BASECAMP_APPLIED'
  | 'BASECAMP_APPROVED'
  | 'BASECAMP_REJECTED'
  | 'BASECAMP_KICKED'
  | 'BASECAMP_CONFIRMED'
  | 'BASECAMP_CANCELED'
  | 'BASECAMP_COMPLETED'
  | 'BASECAMP_MEMBER_CHANGED'
  | 'MEMBER_REPORT_RESOLVED'
  | 'SANCTION_CONFIRMED'
  | 'PROGRAM_APPLICATION_CONFIRMED'
  | 'PROGRAM_APPLICATION_CANCELED'
  | 'PROGRAM_APPLICATION_EXPIRED';

export interface NotificationItem {
  notificationId: number;
  type: NotificationType;
  title: string;
  body: string;
  // 연결할 화면이 없으면 null이다. 값이 있으면 앱 안의 경로다.
  link: string | null;
  // 아직 읽지 않았으면 null이다.
  readAt: string | null;
  createdAt: string;
}

export interface NotificationSetting {
  type: NotificationType;
  emailEnabled: boolean;
}

// 회원·후기 신고와 관리자 처리

export type ReportKind = 'MEMBER' | 'REVIEW';

export type ReportType =
  | 'NO_SHOW'
  | 'MONEY_REQUEST'
  | 'HARASSMENT_OR_THREAT'
  | 'OFFENSIVE_BEHAVIOR'
  | 'FAKE_PROFILE'
  | 'ILLEGAL_CAMPING_INDUCEMENT'
  | 'INAPPROPRIATE_REVIEW';

export type ReportStatus = 'RECEIVED' | 'IN_REVIEW' | 'ACTIONED' | 'DISMISSED';

export type SanctionType = 'WARNING' | 'SUSPEND_7D' | 'SUSPEND_30D' | 'PERMANENT' | 'TEMPORARY_72H';

export type SanctionStatus = 'ACTIVE' | 'LIFTED' | 'EXPIRED';

export interface MemberReportCreateRequest {
  targetMemberId: number;
  basecampId: number;
  kind: ReportKind;
  // 후기 신고(REVIEW)일 때만 보낸다.
  companionReviewId?: number;
  type: ReportType;
  content: string;
}

export interface MemberReportCreateResponse {
  reportId: number;
  status: ReportStatus;
}

// 관리자 화면

export interface AdminMemberRef {
  memberId: number;
  nickname: string;
}

export interface AdminReportSummary {
  reportId: number;
  kind: ReportKind;
  type: ReportType;
  urgent: boolean;
  status: ReportStatus;
  reporter: AdminMemberRef;
  target: AdminMemberRef;
  basecampId: number;
  createdAt: string;
}

export interface AdminReportBasecamp {
  basecampId: number;
  title: string;
  status: BasecampStatus;
  startDate: string;
}

export interface AdminReportReview {
  comment: string;
  tags: string[];
  rejoinWanted: boolean;
  hidden: boolean;
}

export interface AdminSanctionHistoryItem {
  sanctionId: number;
  type: SanctionType;
  // 단계가 없는 제재(임시 정지 등)는 null이다.
  level: number | null;
  status: SanctionStatus;
  reason: string;
  startsAt: string;
  endsAt: string | null;
  liftedAt: string | null;
}

// 후기 신고일 때만 companionReview가 있다. 처리 전에는 resultNote, handledBy, handledAt이 null이다.
export interface AdminReportDetail extends AdminReportSummary {
  content: string;
  resultNote: string | null;
  handledBy: number | null;
  handledAt: string | null;
  basecamp: AdminReportBasecamp | null;
  companionReview: AdminReportReview | null;
  sanctionHistory: AdminSanctionHistoryItem[];
}

// 관리자가 새로 내릴 수 있는 제재 종류다. 임시 정지는 서버가 신고 접수 때 만든다.
export type ConfirmableSanctionType = Exclude<SanctionType, 'TEMPORARY_72H'>;

export interface AdminReportActionRequest {
  sanction: { type: ConfirmableSanctionType; reason: string } | null;
  hideReview: boolean;
  note?: string;
}

export interface AdminReportActionResponse {
  reportId: number;
  status: ReportStatus;
  sanctionId: number | null;
}

export type AdminSpotStatus = 'PENDING_REVIEW' | 'HIDDEN';

export interface AdminSpotSummary {
  spotId: number;
  type: SpotType;
  name: string;
  status: string;
  lat: number;
  lng: number;
  parkWarning: boolean;
  // 공공데이터 장소는 null이다.
  reporter: { memberId: number; nickname: string } | null;
  reportCount: number;
  reasonCounts: Record<BakjiReportReason, number>;
  recentReports: { reason: BakjiReportReason; content: string | null; createdAt: string }[];
  statusChangedAt: string;
}

// 공식 행사

export type ProgramStatus = 'UPCOMING' | 'OPEN' | 'CLOSED' | 'CANCELED';

export interface ProgramSummary {
  programId: number;
  title: string;
  locationText: string;
  // 지도 장소와 연결하지 않은 행사는 null이다.
  spotId: number | null;
  startAt: string;
  endAt: string;
  applyOpenAt: string;
  applyCloseAt: string;
  capacity: number;
  remainingSeats: number;
  fee: number;
  overnight: boolean;
  status: ProgramStatus;
}

export type ProgramApplicationStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'CANCELED' | 'EXPIRED';

export interface ProgramMyApplication {
  applicationId: number;
  status: ProgramApplicationStatus;
  paymentDueAt: string | null;
}

export interface ProgramDetail extends ProgramSummary {
  description: string;
  paymentDeadlineMinutes: number;
  // 비로그인이거나 신청이 없으면 필드가 없다.
  myApplication?: ProgramMyApplication;
}

export interface ProgramApplyResponse {
  applicationId: number;
  status: ProgramApplicationStatus;
  paymentDueAt: string;
  amount: number;
}

export interface ProgramPayResponse {
  applicationId: number;
  status: ProgramApplicationStatus;
  paidAt: string;
}

export interface ProgramCancelResponse {
  status: ProgramApplicationStatus;
  refunded: boolean;
}

export type ProgramCancelReason = 'USER' | 'EXPIRED' | 'SANCTIONED' | 'PROGRAM_CANCELED' | 'WITHDRAWN';

export interface MyProgramApplicationItem {
  applicationId: number;
  status: ProgramApplicationStatus;
  paymentDueAt: string | null;
  confirmedAt: string | null;
  canceledAt: string | null;
  cancelReason: ProgramCancelReason | null;
  createdAt: string;
  program: {
    programId: number;
    title: string;
    locationText: string;
    startAt: string;
    endAt: string;
    fee: number;
    status: ProgramStatus;
  };
}

// 커뮤니티

export type CommunityReportReason = 'SPAM' | 'ABUSE' | 'ILLEGAL_CAMPING' | 'PRIVACY' | 'MONEY_SCAM' | 'OTHER';

export interface CommunityAuthor {
  memberId: number;
  nickname: string;
}

// 연결한 장소가 ACTIVE일 때만 서버가 준다.
export interface CommunityPostSpot {
  spotId: number;
  name: string;
}

interface CommunityPostBase {
  postId: number;
  title: string;
  author: CommunityAuthor;
  spot?: CommunityPostSpot;
  likeCount: number;
  commentCount: number;
  createdAt: string;
}

export interface CommunityPostSummary extends CommunityPostBase {
  // 본문 앞 100자
  excerpt: string;
  thumbnailUrl?: string;
  imageCount: number;
}

export interface CommunityPostImage {
  imageId: number;
  // 서버가 응답마다 새로 발급하는 사전 서명 URL이고 1시간 동안 쓸 수 있다.
  url: string;
}

export interface CommunityPostDetail extends CommunityPostBase {
  content: string;
  images: CommunityPostImage[];
  updatedAt: string;
  // 로그인한 요청에만 있다.
  likedByMe?: boolean;
}

export interface CommunityPostCreateRequest {
  title: string;
  content: string;
  spotId?: number;
  imageIds: number[];
}

// 보내지 않은 필드는 그대로 두고, spotId: null은 장소 연결을 끊는다.
export interface CommunityPostUpdateRequest {
  title?: string;
  content?: string;
  spotId?: number | null;
  imageIds?: number[];
}

// 삭제된 댓글은 보이는 답글이 있을 때만 deleted: true로 오고, author와 content는 빠진다.
export interface CommunityComment {
  commentId: number;
  author?: CommunityAuthor;
  content?: string;
  deleted: boolean;
  createdAt?: string;
  updatedAt?: string;
  replies: CommunityComment[];
}

export interface CommunityCommentCreateRequest {
  content: string;
  parentId?: number;
}

export interface CommunityLikeResponse {
  liked: boolean;
  likeCount: number;
}

export interface CommunityReportRequest {
  reason: CommunityReportReason;
  content?: string;
}

export interface CommunityImageUploadRequest {
  contentType: string;
  sizeBytes: number;
}

export interface CommunityImageUpload {
  imageId: number;
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
}
