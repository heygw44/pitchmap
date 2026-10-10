// 가입 화면의 이메일 칸은 아이디와 도메인을 따로 받는다. 화면 상태와 서버로 보낼 주소를 서로 바꾸는 함수를 둔다.

export type EmailValue = {
  local: string;
  domainChoice: string;
  customDomain: string;
};

// 도메인 select에서 "직접 입력"을 나타내는 값. 실제 도메인과 겹치지 않는다.
export const CUSTOM_DOMAIN = 'custom';

export const EMAIL_DOMAINS = ['naver.com', 'gmail.com', 'daum.net', 'hanmail.net', 'kakao.com', 'nate.com'] as const;

export const EMPTY_EMAIL: EmailValue = { local: '', domainChoice: '', customDomain: '' };

// 대소문자는 바꾸지 않는다. 서버가 소문자로 저장한다.
export function composeEmail(value: EmailValue): string {
  const domain = value.domainChoice === CUSTOM_DOMAIN ? value.customDomain : value.domainChoice;
  return `${value.local.trim()}@${domain.trim()}`;
}

// 브라우저 자동 완성이나 붙여넣기는 전체 주소를 아이디 칸에 넣을 수 있다. 마지막 @를 기준으로 나눠 도메인 칸에 채운다.
export function splitEmail(raw: string, current: EmailValue): EmailValue {
  const at = raw.lastIndexOf('@');
  if (at < 0) return { ...current, local: raw };
  const local = raw.slice(0, at).trim();
  const domain = raw.slice(at + 1).trim();
  if (domain === '') return { ...current, local };
  if ((EMAIL_DOMAINS as readonly string[]).includes(domain.toLowerCase())) {
    return { local, domainChoice: domain.toLowerCase(), customDomain: '' };
  }
  return { local, domainChoice: CUSTOM_DOMAIN, customDomain: domain };
}
