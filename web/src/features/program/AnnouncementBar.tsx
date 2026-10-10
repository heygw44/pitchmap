import { useState } from 'react';
import { Link, useLocation } from '../../app/router';
import { Icon } from '../../components/icons';
import { readStored, writeStored } from '../../lib/storage';
import { useOpenProgramAnnouncement } from './useOpenProgramAnnouncement';

// 닫은 행사의 ID만 기억한다. 다른 행사가 신청을 받기 시작하면 띠가 다시 보인다.
const DISMISSED_KEY = 'pitchmap.dismissedProgramId';

// 화면 맨 위의 얇은 띠로, 지금 신청을 받는 공식 행사 하나를 알린다. 신청 중인 행사가 없으면 아무것도 그리지 않는다.
export function AnnouncementBar() {
  const program = useOpenProgramAnnouncement();
  const { pathname } = useLocation();
  const [dismissedId, setDismissedId] = useState(() => readStored(DISMISSED_KEY));

  if (!program) return null;
  const id = String(program.programId);
  // 이미 그 행사 화면에 와 있으면 같은 안내를 겹쳐 보이지 않는다.
  if (dismissedId === id || pathname === `/programs/${id}`) return null;

  function dismiss() {
    writeStored(DISMISSED_KEY, id);
    setDismissedId(id);
  }

  return (
    <div className="shrink-0 border-b border-contour bg-forest-soft text-forest-deep">
      <div className="mx-auto flex max-w-screen-xl items-center gap-2 pl-4 pr-1">
        <Link
          to={`/programs/${id}`}
          className="flex min-h-11 min-w-0 flex-1 items-center gap-2 text-sm underline-offset-2 hover:underline"
        >
          <Icon name="calendar" size={18} className="shrink-0" />
          <span className="truncate">
            <span className="font-semibold">공식 행사</span> · {program.title} 신청을 받고 있어요
          </span>
          <span className="shrink-0">
            남은 자리 <span className="font-mono tabular-nums">{program.remainingSeats}</span>
          </span>
        </Link>
        <button
          type="button"
          onClick={dismiss}
          aria-label="행사 알림 닫기"
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-control hover:bg-card"
        >
          <Icon name="close" size={18} />
        </button>
      </div>
    </div>
  );
}
