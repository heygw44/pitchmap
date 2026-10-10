// 배너를 닫았다는 표시처럼 이 브라우저에만 남겨도 되는 값만 둔다. 개인정보·토큰은 넣지 않는다.
// 사생활 보호 모드나 저장소 차단 환경에서는 localStorage 접근이 예외를 던지므로, 읽기는 null로, 쓰기는 무시로 처리한다.
export function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeStored(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 저장하지 못하면 이번 방문 동안만 닫힌 상태로 둔다.
  }
}
