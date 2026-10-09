import { useEffect, useState } from 'react';

// active인 동안 1초마다 지금 시각(밀리초)을 새로 돌려준다. 결제 기한 카운트다운에 쓰고, 기준은 브라우저 시계다.
export function useNow(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [active]);

  return now;
}
