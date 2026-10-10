// 입력란이 비었을 때 서버에 보내기 전에 보여 주는 안내 문구다. 가입과 로그인 화면이 함께 쓴다.
// 비밀번호·닉네임이 규칙에 맞는지는 서버가 검사하고, 화면은 빈 칸만 미리 막는다.
export const EMAIL_REQUIRED = '이메일을 입력해 주세요.';
export const PASSWORD_REQUIRED = '비밀번호를 입력해 주세요.';
export const PASSWORD_CONFIRM_REQUIRED = '비밀번호를 한 번 더 입력해 주세요.';
export const NICKNAME_REQUIRED = '닉네임을 입력해 주세요.';
export const EMAIL_DOMAIN_REQUIRED = '이메일 도메인을 골라 주세요.';
export const EMAIL_CUSTOM_DOMAIN_REQUIRED = '도메인을 입력해 주세요.';

// 비밀번호 확인이 비밀번호와 다른지는 서버로 보내기 전에 화면도 확인하므로 안내 문구도 여기 둔다.
export const PASSWORD_CONFIRM_MISMATCH = '비밀번호가 서로 달라요.';
