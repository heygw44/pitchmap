const SDK_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';
const LOAD_TIMEOUT_MS = 15_000;

export type MapLoadErrorReason = 'NO_KEY' | 'LOAD_FAILED';

const MESSAGES: Record<MapLoadErrorReason, string> = {
  NO_KEY: '지도 키가 설정되지 않았어요.',
  LOAD_FAILED: '지도를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.',
};

export class MapLoadError extends Error {
  readonly reason: MapLoadErrorReason;

  constructor(reason: MapLoadErrorReason) {
    super(MESSAGES[reason]);
    this.name = 'MapLoadError';
    this.reason = reason;
  }
}

// 여러 화면이 동시에 지도를 열어도 스크립트는 한 번만 넣도록 진행 중인 약속을 공유한다.
// 실패하면 비워 두어서 다음 호출이 처음부터 다시 시도할 수 있게 한다.
let pending: Promise<void> | null = null;

export function loadKakaoMaps(): Promise<void> {
  pending ??= startLoading().catch((error: unknown) => {
    pending = null;
    throw error;
  });
  return pending;
}

function startLoading(): Promise<void> {
  const key = import.meta.env.VITE_KAKAO_JS_KEY?.trim();
  if (!key) {
    return Promise.reject(new MapLoadError('NO_KEY'));
  }

  return new Promise<void>((resolve, reject) => {
    let script: HTMLScriptElement | null = null;
    let settled = false;

    const timer = window.setTimeout(fail, LOAD_TIMEOUT_MS);

    function succeed() {
      if (settled) return;
      settled = true;
      window.clearTimeout(timer);
      resolve();
    }

    function fail() {
      if (settled) return;
      settled = true;
      window.clearTimeout(timer);
      // 실패한 script 태그를 남겨 두면 다시 시도할 때 태그가 쌓이므로 지운다.
      script?.remove();
      reject(new MapLoadError('LOAD_FAILED'));
    }

    // autoload=false로 불러오면 SDK 나머지 파일은 kakao.maps.load를 불러야 받는다.
    function finishWithSdk() {
      const maps = window.kakao?.maps;
      if (!maps) {
        fail();
        return;
      }
      maps.load(succeed);
    }

    // 앞선 시도가 시간 초과로 끝난 뒤에 스크립트가 늦게 도착했다면 다시 넣지 않고 그대로 쓴다.
    if (window.kakao?.maps) {
      finishWithSdk();
      return;
    }

    script = document.createElement('script');
    script.src = `${SDK_URL}?appkey=${encodeURIComponent(key)}&autoload=false`;
    script.async = true;
    script.addEventListener('load', finishWithSdk);
    script.addEventListener('error', fail);
    document.head.append(script);
  });
}
