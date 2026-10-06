import type { PublicFacilities, SignalLevel, SpotType } from '../../api/types';
import type { IconName } from '../../components/iconPaths';

type SpotTypeTone = 'forest' | 'earth';

type SpotTypeMeta = {
  label: string;
  icon: IconName;
  tone: SpotTypeTone;
  // 목록 행과 상세 머리글의 유형 아이콘 칸 색. Tailwind가 소스 글자를 읽으므로 클래스 이름을 통째로 적는다.
  tileClass: string;
};

export const SPOT_TYPE_META: Record<SpotType, SpotTypeMeta> = {
  CAMPSITE: { label: '공공 야영장', icon: 'tent', tone: 'forest', tileClass: 'bg-forest-soft text-forest-deep' },
  FOREST: { label: '자연휴양림', icon: 'tree', tone: 'forest', tileClass: 'bg-forest-soft text-forest-deep' },
  BAKJI: { label: '박지', icon: 'backpack', tone: 'earth', tileClass: 'bg-earth-soft text-earth-strong' },
};

export const SIGNAL_LEVEL_LABELS: Record<SignalLevel, string> = {
  NONE: '없음',
  WEAK: '약함',
  GOOD: '좋음',
};

// 화면에 보여 줄 순서대로 적는다.
export const FACILITY_LABELS: ReadonlyArray<readonly [keyof PublicFacilities, string]> = [
  ['toiletCount', '화장실 수'],
  ['showerCount', '샤워실 수'],
  ['sinkCount', '개수대 수'],
  ['brazier', '화로대'],
  ['amenities', '부대시설'],
  ['amenitiesEtc', '부대시설 기타'],
  ['nearbyFacilities', '주변 시설'],
  ['nearbyFacilitiesEtc', '주변 시설 기타'],
  ['petPolicy', '반려동물'],
];

const SOURCE_LABELS: Record<string, string> = {
  GOCAMPING: '한국관광공사 고캠핑',
  FOREST: '산림청 국립자연휴양림관리소',
  KDPA: '한국보호지역통합DB(KDPA)',
};

// 서버가 아직 표에 없는 출처 코드를 주면 받은 글자를 그대로 보여 준다.
export function sourceLabel(code: string): string {
  return SOURCE_LABELS[code] ?? code;
}
