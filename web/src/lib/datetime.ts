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
