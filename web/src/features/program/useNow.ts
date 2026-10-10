import { useEffect, useState } from 'react';
import { serverNow } from '../../api/client';

// active인 동안 1초마다 지금 시각(밀리초)을 새로 돌려준다. 결제 기한 카운트다운에 쓰고, 기준은 서버 응답의 Date 헤더로 보정한 시각이다.
export function useNow(active: boolean): number {
  const [now, setNow] = useState(() => serverNow());

  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(serverNow()), 1000);
    return () => window.clearInterval(timer);
  }, [active]);

  return now;
}
