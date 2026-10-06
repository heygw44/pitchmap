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

// 영역 안 장소가 너무 많으면 서버가 칸마다 장소 수와 칸 중심 좌표를 묶어서 준다.
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
  // 지도 화면의 가로·세로 크기(CSS 픽셀, 1~10000). 서버는 묶음 칸 크기를 정하는 데만 쓴다.
  width: number;
  height: number;
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

// 지금 서버는 warned만 준다. 공원 이름, 경계 데이터 출처·기준일, 안내 문구는 경고 상세 기능이 붙으면 함께 온다.
export interface ParkWarning {
  warned: boolean;
  areaName?: string;
  source?: string;
  sourceDate?: string;
  notice?: string;
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

export interface ShortTermForecast {
  at: string;
  temperature: number;
  precipitationProbability: number;
  windSpeed: number;
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
  // 중기 예보 항목의 모양은 날씨 연동 기능에서 정한다.
  midTerm: unknown[];
  sun: SunInfo;
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
  // 항목 모양은 장소 후기, 베이스캠프 기능에서 정한다. 지금 서버는 빈 배열을 준다.
  recentReviews: unknown[];
  expectedPeople: ExpectedPeople[];
  recruitingBasecamps: unknown[];
  // 날씨 연동 전이거나 외부 API가 실패하면 null이다.
  weather: Weather | null;
}
