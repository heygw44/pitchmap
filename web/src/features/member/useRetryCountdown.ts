import { useCallback, useEffect, useState } from 'react';

// 서버가 잠시 막았을 때(429의 Retry-After, 로그인 잠금, 코드 재발송 간격) 남은 초를 1초마다 줄인다.
// 0이 되면 멈춘다.
export function useRetryCountdown(): { secondsLeft: number; start(seconds: number): void } {
  const [secondsLeft, setSecondsLeft] = useState(0);
  const counting = secondsLeft > 0;

  useEffect(() => {
    if (!counting) return;
    const timer = window.setInterval(() => setSecondsLeft((seconds) => Math.max(0, seconds - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [counting]);

  const start = useCallback((seconds: number) => setSecondsLeft(Math.max(0, Math.ceil(seconds))), []);

  return { secondsLeft, start };
}
