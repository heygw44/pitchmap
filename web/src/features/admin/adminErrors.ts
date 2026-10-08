import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';

// 잘못된 제재 단계처럼 서버가 입력 오류의 구체적인 이유를 문구로 주면, 표의 일반 문구 대신 그 문구를 보여 준다.
export function adminErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length === 0 && error.serverMessage) {
    return error.serverMessage;
  }
  return toUserMessage(error);
}
