import { errorMessage } from './errors';
import type { ErrorBody, FieldErrorItem } from './types';

const GENERIC_ERROR_MESSAGE = '예상하지 못한 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.';
const CSRF_COOKIE = 'XSRF-TOKEN';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly serverMessage: string;
  readonly traceId?: string;
  readonly fieldErrors: FieldErrorItem[];
  readonly retryAfterSeconds?: number;
  readonly body: ErrorBody | null;

  constructor(status: number, code: string, body: ErrorBody | null, retryAfterSeconds?: number) {
    // 화면에 보여 줄 문구는 코드 표를 먼저 보고, 표에 없으면 서버 문구를 쓴다.
    super(errorMessage(code) ?? body?.message ?? GENERIC_ERROR_MESSAGE);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.serverMessage = body?.message ?? '';
    this.traceId = body?.traceId;
    this.fieldErrors = body?.fieldErrors ?? [];
    this.retryAfterSeconds = retryAfterSeconds;
    this.body = body;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  query?: Record<string, string | number | boolean | readonly string[] | null | undefined>;
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
  skipUnauthorizedHandler?: boolean;
}

let unauthorizedHandler: (() => void) | null = null;

// 세션이 없거나 끊겼을 때(401) 화면이 로그인으로 보내도록 처리기를 하나 등록한다.
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

// 행사 신청·결제처럼 서버가 중복 처리를 막아야 하는 요청에 붙인다.
// 사용자 행동 한 번에 하나를 만들고, 같은 행동을 다시 보낼 때는 같은 키를 쓴다.
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

function readCookie(name: string): string | undefined {
  for (const pair of document.cookie.split(';')) {
    const separator = pair.indexOf('=');
    if (separator < 0) {
      continue;
    }
    if (pair.slice(0, separator).trim() === name) {
      return decodeURIComponent(pair.slice(separator + 1).trim());
    }
  }
  return undefined;
}

let csrfCookieRequest: Promise<void> | null = null;

// 서버는 모든 API 응답에 XSRF-TOKEN 쿠키를 실어 보낸다. 로그인하지 않았을 때 오는 401 응답도 마찬가지다.
// 그래서 첫 화면에서 쿠키가 아직 없으면 가벼운 GET /api/me를 한 번 보내 쿠키만 받고, 응답 내용은 쓰지 않는다.
// 여러 요청이 동시에 쿠키를 찾아도 서버에는 한 번만 보낸다.
async function ensureCsrfCookie(): Promise<void> {
  csrfCookieRequest ??= fetch('/api/me', {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
    .then(
      () => undefined,
      () => undefined,
    )
    .finally(() => {
      csrfCookieRequest = null;
    });
  await csrfCookieRequest;
}

function buildUrl(path: string, query: RequestOptions['query']): string {
  if (!query) {
    return path;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === null || value === undefined) {
      continue;
    }
    if (Array.isArray(value)) {
      // 서버는 여러 값을 쉼표로 이어 받는다. 빈 목록은 조건이 없는 것과 같아서 보내지 않는다.
      if (value.length > 0) {
        params.set(key, value.join(','));
      }
      continue;
    }
    params.set(key, String(value));
  }
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}

// Retry-After는 보통 초 단위 정수다. HTTP 날짜 형식이 오면 지금부터 남은 초로 바꾼다.
function parseRetryAfter(header: string | null): number | undefined {
  if (!header) {
    return undefined;
  }
  const seconds = Number(header);
  if (Number.isFinite(seconds)) {
    return Math.max(0, Math.ceil(seconds));
  }
  const date = Date.parse(header);
  if (Number.isNaN(date)) {
    return undefined;
  }
  return Math.max(0, Math.ceil((date - Date.now()) / 1000));
}

// 서버 오류 본문을 믿지 않고 쓰는 필드마다 모양을 확인해서 옮긴다. 코드나 문구가 없으면 null이다.
function toErrorBody(value: unknown): ErrorBody | null {
  if (typeof value !== 'object' || value === null || !('code' in value) || !('message' in value)) {
    return null;
  }
  if (typeof value.code !== 'string' || typeof value.message !== 'string') {
    return null;
  }
  const body: ErrorBody = { code: value.code, message: value.message };
  if ('traceId' in value && typeof value.traceId === 'string') {
    body.traceId = value.traceId;
  }
  if ('fieldErrors' in value && Array.isArray(value.fieldErrors)) {
    body.fieldErrors = value.fieldErrors.flatMap((item: unknown): FieldErrorItem[] =>
      typeof item === 'object' &&
      item !== null &&
      'field' in item &&
      'reason' in item &&
      typeof item.field === 'string' &&
      typeof item.reason === 'string'
        ? [{ field: item.field, reason: item.reason }]
        : [],
    );
  }
  if ('suspendedUntil' in value && typeof value.suspendedUntil === 'string') {
    body.suspendedUntil = value.suspendedUntil;
  }
  return body;
}

// 본문이 JSON이 아니면(프록시가 만든 오류 화면 등) 상태 코드로 공통 오류 코드를 고른다.
function codeFromStatus(status: number): string {
  switch (status) {
    case 400:
      return 'INVALID_INPUT';
    case 401:
      return 'AUTHENTICATION_REQUIRED';
    case 403:
      return 'ACCESS_DENIED';
    case 404:
      return 'NOT_FOUND';
    case 405:
      return 'METHOD_NOT_ALLOWED';
    case 415:
      return 'UNSUPPORTED_MEDIA_TYPE';
    case 429:
      return 'TOO_MANY_REQUESTS';
    default:
      return 'INTERNAL_ERROR';
  }
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers = new Headers({ Accept: 'application/json' });

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey);
  }
  if (method !== 'GET') {
    let csrfToken = readCookie(CSRF_COOKIE);
    if (csrfToken === undefined) {
      await ensureCsrfCookie();
      csrfToken = readCookie(CSRF_COOKIE);
    }
    if (csrfToken !== undefined) {
      headers.set('X-XSRF-TOKEN', csrfToken);
    }
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    // 호출하는 쪽이 요청을 취소한 경우는 오류 화면을 띄우지 않도록 그대로 넘긴다.
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    // 서버에 닿지 못해서 본문이 없다. 화면 문구는 오류 코드 표의 네트워크 문구를 쓴다.
    throw new ApiError(0, 'NETWORK_ERROR', null);
  }

  const text = response.status === 204 ? '' : await response.text();

  if (response.ok) {
    if (text === '') {
      return undefined as T;
    }
    const data = parseJson(text);
    if (data === undefined) {
      throw new ApiError(response.status, 'INTERNAL_ERROR', null);
    }
    return data as T;
  }

  const body = text === '' ? null : toErrorBody(parseJson(text));
  const retryAfterSeconds = response.status === 429 ? parseRetryAfter(response.headers.get('Retry-After')) : undefined;
  const error = new ApiError(response.status, body?.code ?? codeFromStatus(response.status), body, retryAfterSeconds);

  if (response.status === 401 && !options.skipUnauthorizedHandler) {
    unauthorizedHandler?.();
  }
  throw error;
}
