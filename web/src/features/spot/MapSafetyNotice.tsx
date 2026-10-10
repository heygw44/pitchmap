import { useState } from 'react';
import { Icon } from '../../components/icons';
import { readStored, writeStored } from '../../lib/storage';

const SEEN_KEY = 'pitchmap.mapSafetyNoticeSeen';

// 지도를 처음 연 사람에게 한 번 보여 주는 일반 안내다. 장소마다 붙는 공원 경계 경고(ParkWarningNotice)는 판단 근거라서
// 닫을 수 없게 두고, 이 안내만 "확인했어요"로 닫을 수 있다. 저장소를 못 쓰는 브라우저에서는 이번 방문 동안만 닫힌다.
export function MapSafetyNotice() {
  const [seen, setSeen] = useState(() => readStored(SEEN_KEY) === '1');

  if (seen) return null;

  function confirm() {
    writeStored(SEEN_KEY, '1');
    setSeen(true);
  }

  return (
    <div
      role="note"
      className="pointer-events-auto flex max-w-md gap-3 rounded-control border-l-4 border-sea bg-sea-soft p-3 shadow-raise"
    >
      <Icon name="info" size={20} className="mt-0.5 shrink-0 text-sea" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="font-semibold text-sea">야영하기 전에 확인해 주세요</p>
        <p className="text-sm text-ink">
          공원 경계는 참고용 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요. 자연공원 안에서는 지정된 야영장
          밖의 야영과 취사가 금지돼 있어요.
        </p>
        <button
          type="button"
          onClick={confirm}
          className="mt-1 inline-flex min-h-11 items-center self-start rounded-control border border-sea px-3 text-sm font-semibold text-sea hover:bg-card"
        >
          확인했어요
        </button>
      </div>
    </div>
  );
}
