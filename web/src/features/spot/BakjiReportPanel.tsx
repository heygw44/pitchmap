import { useEffect, useRef, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { createBakji } from '../../api/bakjis';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { BakjiCreateRequest, BakjiSubmissionResponse, GroundType, SignalLevel } from '../../api/types';
import { Link } from '../../app/router';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import type { LatLng } from '../../map/types';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { ParkWarningNotice } from './ParkWarningNotice';
import { GROUND_TYPE_LABELS, SIGNAL_LEVEL_LABELS } from './spotLabels';

type BakjiReportPanelProps = {
  draft: LatLng | null;
  onCancel: () => void;
  onSubmitted: (response: BakjiSubmissionResponse) => void;
};

type YesNo = 'YES' | 'NO';
type Unknown = 'UNKNOWN';

type FormField = 'location' | 'name' | 'description' | 'hasWater' | 'hasToilet' | 'signalLevel' | 'groundType';

type FieldErrors = Partial<Record<FormField, string>>;

const NAME_MAX = 100;
const DESCRIPTION_MAX = 2000;

const YES_NO_OPTIONS: ChoiceOption<YesNo>[] = [
  { value: 'YES', label: '있음' },
  { value: 'NO', label: '없음' },
];

const SIGNAL_OPTIONS: ChoiceOption<Unknown | SignalLevel>[] = [
  { value: 'UNKNOWN', label: '모름' },
  { value: 'NONE', label: SIGNAL_LEVEL_LABELS.NONE },
  { value: 'WEAK', label: SIGNAL_LEVEL_LABELS.WEAK },
  { value: 'GOOD', label: SIGNAL_LEVEL_LABELS.GOOD },
];

const GROUND_OPTIONS: ChoiceOption<Unknown | GroundType>[] = [
  { value: 'UNKNOWN', label: '모름' },
  ...(Object.keys(GROUND_TYPE_LABELS) as GroundType[]).map((value) => ({ value, label: GROUND_TYPE_LABELS[value] })),
];

const PRIMARY_LINK_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-forest bg-forest px-4 text-base font-semibold text-white hover:border-forest-strong hover:bg-forest-strong';
const SECONDARY_LINK_CLASS =
  'inline-flex min-h-11 items-center justify-center rounded-control border border-ink-subtle bg-card px-4 text-base font-semibold text-ink hover:bg-paper-deep';

// 서버가 지도 영역 밖 좌표를 lat, lng 두 필드로 나눠 알려 주므로, 위치 한 곳에 중복 없이 모아 보여 준다.
function mergeReasons(reasons: string[]): string {
  return [...new Set(reasons)].join(' ');
}

const SERVER_FIELD_MAP: Record<string, FormField> = {
  lat: 'location',
  lng: 'location',
  name: 'name',
  description: 'description',
  hasWater: 'hasWater',
  hasToilet: 'hasToilet',
  signalLevel: 'signalLevel',
  groundType: 'groundType',
};

export function BakjiReportPanel({ draft, onCancel, onSubmitted }: BakjiReportPanelProps) {
  const session = useSession();
  const showSessionSkeleton = useDelayedFlag(session.status === 'loading');
  const [result, setResult] = useState<BakjiSubmissionResponse | null>(null);

  let body: ReactNode;
  if (result) {
    body = <SubmittedView result={result} />;
  } else if (session.status === 'loading') {
    body = showSessionSkeleton ? (
      <div aria-busy="true" className="flex flex-col gap-3">
        <Skeleton className="h-7 w-1/2" />
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    ) : null;
  } else if (session.status === 'anonymous' || session.me === null) {
    body = (
      <EmptyState
        title="로그인하면 박지를 제보할 수 있어요"
        description="제보한 박지는 지도에 올라가고, 다른 회원이 확인하거나 신고할 수 있어요."
        action={
          <Link to={withNext('/login', '/bakjis/new')} className={PRIMARY_LINK_CLASS}>
            로그인
          </Link>
        }
      />
    );
  } else {
    body = (
      <ReportForm
        draft={draft}
        unverified={session.me.status === 'UNVERIFIED'}
        onSubmitted={(response) => {
          setResult(response);
          onSubmitted(response);
        }}
      />
    );
  }

  return (
    <div className="flex flex-col">
      <div className="sticky top-0 z-10 flex items-center border-b border-contour bg-card px-2">
        <button
          type="button"
          aria-label="지도로 돌아가기"
          onClick={onCancel}
          className="flex size-11 items-center justify-center rounded-control text-ink hover:bg-paper-deep"
        >
          <Icon name="chevronLeft" size={24} />
        </button>
      </div>
      <div className="px-4 py-4">{body}</div>
    </div>
  );
}

function ReportForm({
  draft,
  unverified,
  onSubmitted,
}: {
  draft: LatLng | null;
  unverified: boolean;
  onSubmitted: (response: BakjiSubmissionResponse) => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [hasWater, setHasWater] = useState<YesNo | null>(null);
  const [hasToilet, setHasToilet] = useState<YesNo | null>(null);
  const [signalLevel, setSignalLevel] = useState<Unknown | SignalLevel>('UNKNOWN');
  const [groundType, setGroundType] = useState<Unknown | GroundType>('UNKNOWN');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const [checkedDraft, setCheckedDraft] = useState(draft);

  // 위치를 새로 찍으면 이전 위치의 오류는 더 맞지 않으므로 지운다. 렌더 중에 비교해서 한 번에 반영한다.
  if (checkedDraft !== draft) {
    setCheckedDraft(draft);
    if (fieldErrors.location) setFieldErrors({ ...fieldErrors, location: undefined });
  }

  // 요청 중에 화면을 벗어나면 응답을 기다리지 않는다.
  useEffect(() => () => controllerRef.current?.abort(), []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || unverified) return;

    const errors: FieldErrors = {};
    if (draft === null) errors.location = '지도를 눌러 박지 위치를 찍어 주세요.';
    if (name.trim() === '') errors.name = '박지 이름을 적어 주세요.';
    // 서버는 식수·화장실을 꼭 받는다. 기본값을 두면 모르고 넘어간 항목이 "없음"으로 저장되므로 직접 고르게 한다.
    if (hasWater === null) errors.hasWater = '식수가 있는지 골라 주세요.';
    if (hasToilet === null) errors.hasToilet = '화장실이 있는지 골라 주세요.';
    setFieldErrors(errors);
    setFormError(null);
    if (draft === null || hasWater === null || hasToilet === null || Object.keys(errors).length > 0) return;

    const request: BakjiCreateRequest = {
      name: name.trim(),
      lat: Math.round(draft.lat * 1e6) / 1e6,
      lng: Math.round(draft.lng * 1e6) / 1e6,
      hasWater: hasWater === 'YES',
      hasToilet: hasToilet === 'YES',
    };
    if (description.trim() !== '') request.description = description.trim();
    if (signalLevel !== 'UNKNOWN') request.signalLevel = signalLevel;
    if (groundType !== 'UNKNOWN') request.groundType = groundType;

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setSubmitting(true);
    try {
      const response = await createBakji(request, controller.signal);
      if (controller.signal.aborted) return;
      onSubmitted(response);
    } catch (error) {
      if (controller.signal.aborted) return;
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const reasons: Partial<Record<FormField, string[]>> = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        const target = SERVER_FIELD_MAP[field];
        if (target) (reasons[target] ??= []).push(reason);
        else others.push(reason);
      }
      const errors: FieldErrors = {};
      for (const [field, list] of Object.entries(reasons)) errors[field as FormField] = mergeReasons(list);
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    // 목록에 없는 선택 값처럼 필드 정보 없이 오는 입력 오류는 서버가 준 문구를 그대로 보여 준다.
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.serverMessage !== '') {
      setFormError(error.serverMessage);
      return;
    }
    setFormError(toUserMessage(error));
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h2 className="font-serif text-xl font-semibold text-ink">박지 제보</h2>

      {unverified && (
        <Notice tone="warning" title="이메일 인증을 마치면 박지를 제보할 수 있어요">
          <Link to="/verify-email" className="inline-flex min-h-11 items-center text-forest underline underline-offset-2">
            이메일 인증하러 가기
          </Link>
        </Notice>
      )}

      {formError && (
        <div role="alert">
          <Notice tone="danger" title="제보하지 못했어요">
            <p>{formError}</p>
          </Notice>
        </div>
      )}

      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium text-ink">위치</p>
        {draft === null ? (
          <p className="text-base text-ink">지도를 눌러 박지 위치를 찍어 주세요</p>
        ) : (
          <>
            <p className="font-mono text-sm tabular-nums text-ink">
              {draft.lat.toFixed(5)}, {draft.lng.toFixed(5)}
            </p>
            <p className="text-sm text-ink-muted">지도를 다시 누르면 위치가 바뀌어요.</p>
          </>
        )}
        {fieldErrors.location && (
          <p role="alert" className="flex items-start gap-1 text-sm text-danger">
            <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
            <span>{fieldErrors.location}</span>
          </p>
        )}
      </div>

      <TextField
        label="이름"
        name="name"
        maxLength={NAME_MAX}
        value={name}
        onChange={(event) => setName(event.target.value)}
        error={fieldErrors.name}
      />
      <TextArea
        label="설명 (선택)"
        name="description"
        maxLength={DESCRIPTION_MAX}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        error={fieldErrors.description}
      />
      <ChoiceGroup
        legend="식수"
        name="hasWater"
        options={YES_NO_OPTIONS}
        value={hasWater}
        onChange={setHasWater}
        error={fieldErrors.hasWater}
      />
      <ChoiceGroup
        legend="화장실"
        name="hasToilet"
        options={YES_NO_OPTIONS}
        value={hasToilet}
        onChange={setHasToilet}
        error={fieldErrors.hasToilet}
      />
      <ChoiceGroup
        legend="통신 상태 (선택)"
        name="signalLevel"
        options={SIGNAL_OPTIONS}
        value={signalLevel}
        onChange={setSignalLevel}
        error={fieldErrors.signalLevel}
      />
      <ChoiceGroup
        legend="바닥 (선택)"
        name="groundType"
        options={GROUND_OPTIONS}
        value={groundType}
        onChange={setGroundType}
        error={fieldErrors.groundType}
      />

      <Button
        type="submit"
        fullWidth
        loading={submitting}
        disabled={unverified}
        disabledReason={unverified ? '이메일 인증을 마치면 제보할 수 있어요' : undefined}
      >
        제보하기
      </Button>
    </form>
  );
}

function SubmittedView({ result }: { result: BakjiSubmissionResponse }) {
  const { parkWarning, guide, duplicateCandidates } = result;

  return (
    <div role="status" className="flex flex-col gap-4">
      <h2 className="font-serif text-xl font-semibold text-ink">제보를 등록했어요</h2>

      {parkWarning.warned ? (
        <ParkWarningNotice warning={parkWarning} />
      ) : (
        <Notice tone="info" title="안내">
          <p>{guide}</p>
        </Notice>
      )}

      {duplicateCandidates.length > 0 && (
        <section aria-labelledby="duplicate-title" className="flex flex-col gap-2">
          <h3 id="duplicate-title" className="text-sm font-semibold text-ink-muted">
            50m 안에 비슷한 박지가 있어요
          </h3>
          <p className="text-sm text-ink">제보는 이미 등록됐어요. 같은 곳이라면 기존 박지에서 정보를 확인해 보세요.</p>
          <ul className="flex flex-col">
            {duplicateCandidates.map((candidate) => (
              <li key={candidate.spotId} className="border-t border-contour first:border-t-0">
                <Link
                  to={`/spots/${candidate.spotId}`}
                  className="flex min-h-11 items-center justify-between gap-3 py-2 text-forest underline underline-offset-2"
                >
                  <span className="min-w-0 break-words">{candidate.name}</span>
                  <span className="shrink-0 font-mono text-sm tabular-nums text-ink-muted">{candidate.distanceM}m</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="flex flex-wrap gap-2">
        <Link to={`/spots/${result.spotId}`} className={PRIMARY_LINK_CLASS}>
          제보한 박지 보기
        </Link>
        <Link to="/" className={SECONDARY_LINK_CLASS}>
          지도로
        </Link>
      </div>
    </div>
  );
}
