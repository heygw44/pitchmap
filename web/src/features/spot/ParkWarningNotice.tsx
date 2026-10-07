import type { ParkWarning } from '../../api/types';
import { Evidence } from '../../components/Evidence';
import { Notice } from '../../components/Notice';
import { sourceLabel } from './spotLabels';

// 서버가 고지·안내 문구를 주지 않을 때 쓰는 문장이다. 서버 문구와 같은 뜻으로 맞춘다.
const FALLBACK_NOTICE = '참고용 데이터예요. 공식 경계는 고시 도면을 확인해 주세요.';
const FALLBACK_GUIDE =
  '자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고, 어기면 과태료 대상이에요. 이곳에서 야영하지 말고 가까운 지정 야영장을 이용해 주세요.';

// 장소 상세와 박지 제보 결과가 함께 쓰는 공원 경계 경고. 판단 근거라서 접거나 숨기지 않는다. 경고가 아니면 아무것도 그리지 않는다.
export function ParkWarningNotice({ warning }: { warning: ParkWarning }) {
  if (!warning.warned) return null;
  return (
    <Notice tone="warning" title="공원 경계 안일 가능성이 있어요">
      <div className="flex flex-col gap-2">
        {warning.areaName && <p className="font-semibold">{warning.areaName}</p>}
        <p>{warning.notice ?? FALLBACK_NOTICE}</p>
        <p>{warning.guide ?? FALLBACK_GUIDE}</p>
        <Evidence
          items={[
            ...(warning.source ? [{ label: '출처', value: sourceLabel(warning.source) }] : []),
            ...(warning.sourceDate ? [{ label: '기준일', value: warning.sourceDate }] : []),
          ]}
        />
      </div>
    </Notice>
  );
}
