import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { fetchSpotDetail } from '../../api/spots';
import type { BakjiDetail, MidTermForecast, PublicDetail, SpotDetail, Weather } from '../../api/types';
import { Link } from '../../app/router';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Evidence } from '../../components/Evidence';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { formatKstTime, formatLocalDate } from '../../lib/datetime';
import { SpotReviews } from '../review/SpotReviews';
import { BakjiFeedback } from './BakjiFeedback';
import { ParkWarningNotice } from './ParkWarningNotice';
import { FACILITY_LABELS, SIGNAL_LEVEL_LABELS, SPOT_TYPE_META } from './spotLabels';

type SpotDetailPanelProps = {
  spotId: number;
  onBack: () => void;
};

// 어느 요청(장소, 시도 횟수)의 결과인지 함께 기억해서, 장소를 바꾸면 이전 결과를 곧바로 버린다.
type DetailResult =
  | { spotId: number; attempt: number; detail: SpotDetail }
  | { spotId: number; attempt: number; error: unknown };

const SECTION_CLASS = 'mt-4 border-t border-contour pt-4';
const SECTION_TITLE_CLASS = 'text-sm font-semibold text-ink-muted';

// 단기 예보는 앞에서부터 이만큼만 보여 준다.
const SHORT_TERM_LIMIT = 4;

// 중기 예보는 앞에서부터 이만큼만 보여 준다.
const MID_TERM_LIMIT = 7;

// 기상청이 주지 않은 값은 글자 대신 줄표로 보여 준다.
function formatValue(value: number | null, unit: string): string {
  return value === null ? '-' : `${value}${unit}`;
}

export function SpotDetailPanel({ spotId, onBack }: SpotDetailPanelProps) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<DetailResult | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchSpotDetail(spotId, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ spotId, attempt, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ spotId, attempt, error });
      },
    );
    return () => controller.abort();
  }, [spotId, attempt]);

  const current = result && result.spotId === spotId && result.attempt === attempt ? result : null;
  const showSkeleton = useDelayedFlag(current === null);
  const loadedName = current && 'detail' in current ? current.detail.name : null;

  useEffect(() => {
    if (loadedName === null) return;
    document.title = `${loadedName} · 피치맵`;
    return () => {
      document.title = '피치맵';
    };
  }, [loadedName]);

  let body: ReactNode;
  if (current === null) {
    body = showSkeleton ? <DetailSkeleton /> : null;
  } else if ('detail' in current) {
    body = <DetailContent detail={current.detail} />;
  } else if (current.error instanceof ApiError && current.error.code === 'NOT_FOUND') {
    body = (
      <EmptyState
        title="장소를 찾을 수 없어요"
        description="삭제됐거나 숨김 처리된 장소예요."
        action={
          <Link
            to="/map"
            className="inline-flex min-h-11 items-center rounded-control border border-ink-subtle bg-card px-4 font-semibold text-ink hover:bg-paper-deep"
          >
            목록으로
          </Link>
        }
      />
    );
  } else {
    body = (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="장소 정보를 불러오지 못했어요">
          {toUserMessage(current.error)}
        </Notice>
        <div>
          <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col">
      <div className="sticky top-0 z-10 flex items-center border-b border-contour bg-card px-2">
        <button
          type="button"
          aria-label="목록으로"
          onClick={onBack}
          className="flex size-11 items-center justify-center rounded-control text-ink hover:bg-paper-deep"
        >
          <Icon name="chevronLeft" size={24} />
        </button>
      </div>
      <div aria-busy={current === null} className="px-4 py-4">
        {body}
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <Skeleton className="h-7 w-2/3" />
      <Skeleton className="h-5 w-1/3" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}

function DetailContent({ detail }: { detail: SpotDetail }) {
  const meta = SPOT_TYPE_META[detail.type];
  const publicDetail = detail.publicDetail;
  const bakji = detail.bakji;
  const warning = detail.parkWarning;
  // 확인 버튼을 누르면 서버가 준 새 횟수로 바꿔 보여 준다. 상세를 다시 불러오지 않는다.
  const [confirmationCount, setConfirmationCount] = useState(bakji?.confirmationCount ?? 0);

  return (
    <article>
      <header className="flex flex-col gap-2">
        <h2 className="font-serif text-xl font-semibold text-ink">{detail.name}</h2>
        <div className="flex flex-wrap gap-1">
          <Badge tone={meta.tone} icon={meta.icon}>
            {meta.label}
          </Badge>
          {publicDetail?.closedNow && (
            <Badge tone="closed" icon="closed">
              휴장
            </Badge>
          )}
          {warning.warned && (
            <Badge tone="warning" icon="alert">
              공원 경계 경고
            </Badge>
          )}
        </div>
        {detail.address !== null && <p className="text-sm text-ink-muted">{detail.address}</p>}
      </header>

      {(publicDetail?.closedNow || warning.warned) && (
        <div className="mt-4 flex flex-col gap-3">
          {publicDetail?.closedNow && <ClosedNotice publicDetail={publicDetail} />}
          <ParkWarningNotice warning={warning} />
        </div>
      )}

      {bakji && (
        <BakjiSection bakji={bakji} confirmationCount={confirmationCount}>
          <BakjiFeedback spotId={detail.spotId} onConfirmed={setConfirmationCount} />
        </BakjiSection>
      )}
      {publicDetail && <PublicSection publicDetail={publicDetail} />}

      <section className={SECTION_CLASS} aria-labelledby="spot-basecamp-title">
        <h3 id="spot-basecamp-title" className={SECTION_TITLE_CLASS}>
          베이스캠프
        </h3>
        <div className="mt-2">
          {warning.warned ? (
            <Button
              variant="secondary"
              disabled
              disabledReason="공원 경계 경고가 있는 박지에서는 베이스캠프를 열 수 없어요."
            >
              이 장소로 베이스캠프 열기
            </Button>
          ) : (
            <Link
              to={`/basecamps/new?spotId=${detail.spotId}`}
              className="inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep"
            >
              이 장소로 베이스캠프 열기
            </Link>
          )}
        </div>
      </section>

      <section className={SECTION_CLASS} aria-labelledby="spot-community-title">
        <h3 id="spot-community-title" className={SECTION_TITLE_CLASS}>
          커뮤니티
        </h3>
        <div className="mt-2 flex flex-wrap gap-2">
          <Link
            to={`/community?spotId=${detail.spotId}`}
            className="inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep"
          >
            이 장소 관련 글 보기
          </Link>
          <Link
            to={`/community/new?spotId=${detail.spotId}`}
            className="inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep"
          >
            이 장소로 글쓰기
          </Link>
        </div>
      </section>

      <SpotReviews key={detail.spotId} spotId={detail.spotId} initialRating={detail.rating} />

      <WeatherSection weather={detail.weather} />
    </article>
  );
}

function ClosedNotice({ publicDetail }: { publicDetail: PublicDetail }) {
  const { closedFrom, closedUntil } = publicDetail;
  let period: string | null = null;
  if (closedFrom !== null && closedUntil !== null) {
    period = `${formatLocalDate(closedFrom)} ~ ${formatLocalDate(closedUntil)}`;
  } else if (closedFrom !== null) {
    period = `${formatLocalDate(closedFrom)}부터`;
  } else if (closedUntil !== null) {
    period = `${formatLocalDate(closedUntil)}까지`;
  }

  // 원천 데이터가 늦게 바뀔 수 있어서 서버는 휴장 장소를 숨기지 않는다. 그래서 확인을 권하는 문장을 함께 둔다.
  return (
    <Notice tone="info" title="지금은 휴장 기간이에요">
      <div className="flex flex-col gap-2">
        {period && <p className="tabular-nums">{period}</p>}
        <p>운영 정보는 원천 데이터 기준이라 실제와 다를 수 있어요. 방문 전에 야영장에 확인해 주세요.</p>
      </div>
    </Notice>
  );
}

function BakjiSection({
  bakji,
  confirmationCount,
  children,
}: {
  bakji: BakjiDetail;
  confirmationCount: number;
  children: ReactNode;
}) {
  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-bakji-title">
      <h3 id="spot-bakji-title" className={SECTION_TITLE_CLASS}>
        박지 정보
      </h3>
      {bakji.description && <p className="mt-2 whitespace-pre-line text-base text-ink">{bakji.description}</p>}
      <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-ink-muted">식수</dt>
        <dd className="text-ink">{bakji.hasWater ? '있음' : '없음'}</dd>
        <dt className="text-ink-muted">화장실</dt>
        <dd className="text-ink">{bakji.hasToilet ? '있음' : '없음'}</dd>
        {bakji.signalLevel !== null && (
          <>
            <dt className="text-ink-muted">통신</dt>
            <dd className="text-ink">{SIGNAL_LEVEL_LABELS[bakji.signalLevel]}</dd>
          </>
        )}
        <dt className="text-ink-muted">확인 횟수</dt>
        <dd className="tabular-nums text-ink">{confirmationCount}회</dd>
        <dt className="text-ink-muted">제보한 회원</dt>
        <dd className="text-ink">{bakji.reporter.nickname}</dd>
      </dl>
      {children}
    </section>
  );
}

// 링크로 열 수 있는 http(s) 주소만 링크로 만든다. 원천 값에 scheme이 없거나 다른 scheme이면 글자로만 보여 준다.
function parseHomepage(homepage: string): URL | null {
  try {
    const url = new URL(homepage);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}

function PublicSection({ publicDetail }: { publicDetail: PublicDetail }) {
  const { category, facilities, phone, homepage } = publicDetail;
  const facilityRows = facilities
    ? FACILITY_LABELS.flatMap(([key, label]) => {
        const value = facilities[key];
        return value === null ? [] : [{ key, label, value }];
      })
    : [];
  const homepageUrl = homepage ? parseHomepage(homepage) : null;

  if (category === null && facilityRows.length === 0 && !phone && !homepage) return null;

  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-public-title">
      <h3 id="spot-public-title" className={SECTION_TITLE_CLASS}>
        시설 정보
      </h3>
      {category !== null && <p className="mt-2 text-base text-ink">{category}</p>}
      {facilityRows.length > 0 && (
        <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3">
          {facilityRows.map((row) => (
            <div key={row.key} className="flex min-w-0 flex-col">
              <dt className="text-sm text-ink-muted">{row.label}</dt>
              <dd className="text-sm break-words text-ink">{row.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {(phone || homepage) && (
        <div className="mt-3 flex flex-col">
          {phone && (
            <a
              href={`tel:${phone.replace(/[^\d+]/g, '')}`}
              className="inline-flex min-h-11 items-center self-start text-forest underline underline-offset-2"
            >
              전화 {phone}
            </a>
          )}
          {homepage &&
            (homepageUrl ? (
              <a
                href={homepageUrl.href}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex min-h-11 items-center self-start text-forest underline underline-offset-2"
              >
                홈페이지 {homepageUrl.hostname}
              </a>
            ) : (
              <p className="py-2 text-sm text-ink">홈페이지 {homepage}</p>
            ))}
        </div>
      )}
    </section>
  );
}

function WeatherSection({ weather }: { weather: Weather | null }) {
  return (
    <section className={SECTION_CLASS} aria-labelledby="spot-weather-title">
      <h3 id="spot-weather-title" className={SECTION_TITLE_CLASS}>
        날씨
      </h3>
      {weather === null ? (
        <p className="mt-2 text-sm text-ink-muted">날씨 정보를 불러오지 못했어요</p>
      ) : (
        <div className="mt-2 flex flex-col gap-3">
          <p className="text-sm text-ink-muted">출처 {weather.source}</p>
          {weather.shortTerm.length > 0 && (
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="text-ink-muted">
                  <th scope="col" className="py-1 font-normal">
                    시각
                  </th>
                  <th scope="col" className="py-1 text-right font-normal">
                    기온
                  </th>
                  <th scope="col" className="py-1 text-right font-normal">
                    강수확률
                  </th>
                  <th scope="col" className="py-1 text-right font-normal">
                    바람
                  </th>
                </tr>
              </thead>
              <tbody className="font-mono tabular-nums text-ink">
                {weather.shortTerm.slice(0, SHORT_TERM_LIMIT).map((forecast) => (
                  <tr key={forecast.at} className="border-t border-contour">
                    <td className="py-2">{formatKstTime(forecast.at)}</td>
                    <td className="py-2 text-right">{formatValue(forecast.temperature, '°C')}</td>
                    <td className="py-2 text-right">{formatValue(forecast.precipitationProbability, '%')}</td>
                    <td className="py-2 text-right">{formatValue(forecast.windSpeed, 'm/s')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {weather.midTerm.length > 0 && <MidTermTable forecasts={weather.midTerm.slice(0, MID_TERM_LIMIT)} />}
          {weather.sun ? (
            <Evidence
              items={[
                { label: '일출', value: weather.sun.sunrise },
                { label: '일몰', value: weather.sun.sunset },
                { label: '시민박명 끝', value: weather.sun.civilTwilightEnd },
              ]}
            />
          ) : (
            <p className="text-sm text-ink-muted">일출·일몰 정보를 불러오지 못했어요</p>
          )}
        </div>
      )}
    </section>
  );
}

function MidTermTable({ forecasts }: { forecasts: MidTermForecast[] }) {
  return (
    <table className="w-full text-left text-sm">
      <caption className="pb-1 text-left text-ink-muted">중기 예보</caption>
      <thead>
        <tr className="text-ink-muted">
          <th scope="col" className="py-1 font-normal">
            날짜
          </th>
          <th scope="col" className="py-1 text-right font-normal">
            최저/최고
          </th>
          <th scope="col" className="py-1 text-right font-normal">
            오전
          </th>
          <th scope="col" className="py-1 text-right font-normal">
            오후
          </th>
        </tr>
      </thead>
      <tbody className="tabular-nums text-ink">
        {forecasts.map((forecast) => (
          <tr key={forecast.date} className="border-t border-contour">
            <td className="py-2">{formatLocalDate(forecast.date)}</td>
            <td className="py-2 text-right font-mono">
              {formatValue(forecast.minTemperature, '°')}/{formatValue(forecast.maxTemperature, '°')}
            </td>
            <td className="py-2 text-right">{forecast.amSky ?? '-'}</td>
            <td className="py-2 text-right">{forecast.pmSky ?? '-'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
