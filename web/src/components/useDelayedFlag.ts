import { useEffect, useState } from 'react';

// active가 delayMs 동안 계속 true일 때만 true를 돌려준다. 빨리 끝나는 로딩에서 스켈레톤이 깜빡이지 않게 하려고 쓴다.
// active가 false가 되면 같은 렌더에서 바로 false를 돌려준다.
export function useDelayedFlag(active: boolean, delayMs = 300): boolean {
  const [elapsed, setElapsed] = useState(false);

  useEffect(() => {
    if (!active) return;
    const timer = window.setTimeout(() => setElapsed(true), delayMs);
    return () => {
      window.clearTimeout(timer);
      setElapsed(false);
    };
  }, [active, delayMs]);

  return active && elapsed;
}
