import { Link } from '../../app/router';
import type { SpotMarker } from '../../api/types';
import { Badge } from '../../components/Badge';
import { Icon } from '../../components/icons';
import { SPOT_TYPE_META } from './spotLabels';

type SpotListRowProps = {
  marker: SpotMarker;
};

// 지도 마커를 누르지 못하는 사람도 같은 장소를 고를 수 있도록, 목록 행 하나가 마커 하나와 짝을 이룬다.
export function SpotListRow({ marker }: SpotListRowProps) {
  const meta = SPOT_TYPE_META[marker.type];

  return (
    <li>
      <Link
        to={`/spots/${marker.spotId}`}
        className="flex min-h-14 items-center gap-3 border-b border-contour px-4 py-3 hover:bg-paper-deep focus-visible:-outline-offset-2"
      >
        <span className={`flex size-8 shrink-0 items-center justify-center rounded-control ${meta.tileClass}`}>
          <Icon name={meta.icon} size={18} />
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="truncate text-base text-ink">{marker.name}</span>
          <span className="flex flex-wrap gap-1">
            <Badge tone="neutral">{meta.label}</Badge>
            {marker.parkWarning && (
              <Badge tone="warning" icon="alert">
                공원 경계 경고
              </Badge>
            )}
            {marker.closedNow && (
              <Badge tone="closed" icon="closed">
                휴장
              </Badge>
            )}
          </span>
        </span>
      </Link>
    </li>
  );
}
