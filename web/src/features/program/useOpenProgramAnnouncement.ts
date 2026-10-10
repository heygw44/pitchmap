import { useEffect, useState } from 'react';
import { listPrograms } from '../../api/programs';
import type { ProgramSummary } from '../../api/types';

// 화면을 옮길 때마다 알림 띠가 새로 그려지므로, 목록 요청을 한 번 받아 두고 이 시간 동안 다시 쓴다.
const CACHE_MS = 5 * 60 * 1000;

let cached: { at: number; promise: Promise<ProgramSummary | null> } | null = null;

// 신청 중인 행사 가운데 자리가 남아 있고 신청 마감 전인 첫 행사를 고른다. 서버는 시작일이 빠른 순서로 준다.
async function fetchAnnouncement(): Promise<ProgramSummary | null> {
  try {
    const page = await listPrograms('OPEN', 0);
    const now = Date.now();
    return page.content.find((program) => program.remainingSeats > 0 && Date.parse(program.applyCloseAt) > now) ?? null;
  } catch {
    // 알림 띠는 덧붙이는 안내라서, 못 받아 오면 띠를 그리지 않을 뿐 사용자에게 오류를 보이지 않는다.
    return null;
  }
}

function loadAnnouncement(): Promise<ProgramSummary | null> {
  const now = Date.now();
  if (!cached || now - cached.at > CACHE_MS) {
    cached = { at: now, promise: fetchAnnouncement() };
  }
  return cached.promise;
}

export function useOpenProgramAnnouncement(): ProgramSummary | null {
  const [program, setProgram] = useState<ProgramSummary | null>(null);

  useEffect(() => {
    let active = true;
    void loadAnnouncement().then((next) => {
      if (active) setProgram(next);
    });
    return () => {
      active = false;
    };
  }, []);

  return program;
}
