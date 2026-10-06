// 로그인 뒤 돌아갈 경로는 주소창의 next 값에서 읽는다. 누구나 이 값을 바꿔 링크를 만들 수 있으므로,
// 화면은 같은 사이트 안의 경로('/'로 시작하고 '//'나 '/\'로 시작하지 않는 값)만 받고 나머지는 버린다.
// '//evil.com'과 '/\evil.com'은 브라우저가 다른 사이트 주소로 해석한다.
export function safeNextPath(search: string): string | null {
  const next = new URLSearchParams(search).get('next');
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) {
    return null;
  }
  return next;
}

// 다음 화면 주소에 돌아갈 경로를 붙인다. 돌아갈 경로가 없으면 그대로 둔다.
export function withNext(path: string, next: string | null): string {
  return next ? `${path}?next=${encodeURIComponent(next)}` : path;
}
