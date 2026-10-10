// 서버는 두 가지 시간 값을 준다.
// 시각(예: 2026-11-07T01:00:00Z)은 UTC라서 화면에 보여 줄 때 한국 시간(Asia/Seoul)으로 바꿔야 한다.
// 날짜(예: 출발일 2026-11-07)는 이미 한국 날짜라서 바꾸지 않는다. new Date('2026-11-07')은 UTC 자정으로 읽히므로,
// 브라우저 시간대가 한국보다 늦으면 하루 앞 날짜가 나온다. 그래서 날짜는 문자열을 직접 나눠서 읽는다.

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const KST_DATE_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

// 브라우저마다 한국어 날짜 사이의 구두점이 달라서, 조각을 받아 직접 이어 붙인다.
function kstParts(iso: string): Partial<Record<Intl.DateTimeFormatPartTypes, string>> {
  const parts: Partial<Record<Intl.DateTimeFormatPartTypes, string>> = {};
  for (const part of KST_DATE_TIME.formatToParts(new Date(iso))) {
    parts[part.type] = part.value;
  }
  return parts;
}

// 예: 11월 7일 (토) 10:00
export function formatKstDateTime(iso: string): string {
  const { month, day, weekday, hour, minute } = kstParts(iso);
  return `${month}월 ${day}일 (${weekday}) ${hour}:${minute}`;
}

// 예: 10:00
export function formatKstTime(iso: string): string {
  const { hour, minute } = kstParts(iso);
  return `${hour}:${minute}`;
}

// YYYY-MM-DD 한국 날짜를 시간대 변환 없이 보여 준다. 예: 11월 7일 (토). 형식이 다르면 받은 문자열을 그대로 돌려준다.
export function formatLocalDate(date: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) {
    return date;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  // 요일만 구하려고 UTC 기준으로 날짜를 만든다. UTC로 만들고 UTC로 읽어서 브라우저 시간대가 끼어들지 않는다.
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return `${month}월 ${day}일 (${weekday})`;
}

const KST_DATE = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' });

// 오늘 한국 날짜를 YYYY-MM-DD로 돌려준다. 브라우저 시간대와 상관없이 한국 기준이다.
// 서버도 방문일이 오늘(한국 날짜)보다 뒤면 거부하므로, 날짜 입력의 최댓값으로 쓴다.
export function todayKst(now: Date = new Date()): string {
  return KST_DATE.format(now);
}

// YYYY-MM-DD 한국 날짜에 일수를 더한 날짜를 돌려준다. 시간대 변환 없이 UTC로 계산해서 읽는다. 형식이 다르면 받은 문자열을 그대로 돌려준다.
export function addDaysToLocalDate(date: string, days: number): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) {
    return date;
  }
  const moved = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]) + days));
  return moved.toISOString().slice(0, 10);
}

// 출발일 0시(한국 시간)의 시각이다. 출발 며칠 전인지 따질 때 기준으로 쓴다.
export function departureInstant(startDate: string): Date {
  return new Date(`${startDate}T00:00:00+09:00`);
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

// 한국 날짜 기준으로 기한까지 남은 일수다. 기한이 오늘이면 0이고, 이미 지났으면 음수다.
export function daysUntilKst(iso: string, now: Date = new Date()): number {
  const target = Date.parse(`${KST_DATE.format(new Date(iso))}T00:00:00+09:00`);
  const today = Date.parse(`${todayKst(now)}T00:00:00+09:00`);
  return Math.round((target - today) / MS_PER_DAY);
}

// 예: 방금, 5분 전, 3시간 전, 10월 7일, 2025년 10월 7일. 하루가 지난 글은 한국 날짜 기준으로 적는다.
export function formatRelativeKst(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const diffMinutes = Math.floor((now.getTime() - then.getTime()) / 60000);
  if (diffMinutes < 1) return '방금';
  if (diffMinutes < 60) return `${diffMinutes}분 전`;
  const thenDate = KST_DATE.format(then);
  if (thenDate === todayKst(now)) return `${Math.floor(diffMinutes / 60)}시간 전`;
  const { month, day } = kstParts(iso);
  if (thenDate.slice(0, 4) === todayKst(now).slice(0, 4)) return `${month}월 ${day}일`;
  return `${thenDate.slice(0, 4)}년 ${month}월 ${day}일`;
}

// 목록의 날짜 칸에 쓰는 한국 시간 기준 월, 일, 요일이다. 예: 11월, 7, 토
export function kstDateBlock(iso: string): { month: string; day: string; weekday: string } {
  const { month, day, weekday } = kstParts(iso);
  return { month: `${month}월`, day: day ?? '', weekday: weekday ?? '' };
}
