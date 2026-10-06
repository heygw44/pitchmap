import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { confirmBakji, reportBakji } from '../../api/bakjis';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { BakjiProblemReportRequest, BakjiReportReason } from '../../api/types';
import { Link, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import type { ChoiceOption } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { TextArea } from '../../components/TextArea';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { REPORT_REASON_LABELS } from './spotLabels';

type BakjiFeedbackProps = {
  spotId: number;
  // 확인에 성공하면 서버가 준 새 확인 횟수를 알린다. 이미 확인했다는 응답에는 횟수가 없어서 부르지 않는다.
  onConfirmed: (confirmationCount: number) => void;
};

const REPORT_CONTENT_MAX = 1000;

const REASON_OPTIONS: ChoiceOption<BakjiReportReason>[] = (Object.keys(REPORT_REASON_LABELS) as BakjiReportReason[]).map(
  (value) => ({ value, label: REPORT_REASON_LABELS[value] }),
);

const UNVERIFIED_REASON = '이메일 인증을 마치면 할 수 있어요';

// 박지를 다녀온 회원이 정보를 확인하거나 문제를 신고한다.
// 상세 응답에는 내가 이미 확인했는지가 없어서 미리 막지 않고, 서버가 이미 확인했다고 답하면 완료 상태로 바꾼다.
export function BakjiFeedback({ spotId, onConfirmed }: BakjiFeedbackProps) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const [confirming, setConfirming] = useState(false);
  const [confirmed, setConfirmed] = useState<'new' | 'already' | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reported, setReported] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  if (session.status === 'loading') return null;

  if (session.status === 'anonymous' || session.me === null) {
    return (
      <div className="mt-3">
        <Link
          to={withNext('/login', pathname + search)}
          className="inline-flex min-h-11 items-center text-forest underline underline-offset-2"
        >
          로그인하면 확인하거나 신고할 수 있어요
        </Link>
      </div>
    );
  }

  const unverified = session.me.status === 'UNVERIFIED';

  async function handleConfirm() {
    if (confirming || confirmed !== null) return;
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setConfirming(true);
    setConfirmError(null);
    try {
      const response = await confirmBakji(spotId, controller.signal);
      if (controller.signal.aborted) return;
      setConfirmed('new');
      onConfirmed(response.confirmationCount);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error instanceof ApiError && error.code === 'BAKJI_ALREADY_CONFIRMED') {
        setConfirmed('already');
      } else {
        setConfirmError(toUserMessage(error));
      }
    } finally {
      if (!controller.signal.aborted) setConfirming(false);
    }
  }

  return (
    <div className="mt-3 flex flex-col gap-3">
      <div className="flex flex-wrap items-start gap-2">
        <Button
          variant="secondary"
          loading={confirming}
          disabled={unverified || confirmed !== null}
          disabledReason={unverified ? UNVERIFIED_REASON : undefined}
          onClick={handleConfirm}
        >
          {confirmed !== null ? '확인했어요' : '다녀왔어요, 정보가 맞아요'}
        </Button>
        <Button
          variant="ghost"
          disabled={unverified || reported}
          disabledReason={unverified ? UNVERIFIED_REASON : undefined}
          onClick={() => setReportOpen(true)}
        >
          문제 신고
        </Button>
      </div>

      {confirmed === 'already' && (
        <p role="status" className="text-sm text-ink-muted">
          이미 이 박지를 확인했어요.
        </p>
      )}
      {confirmError && (
        <div role="alert">
          <Notice tone="danger" title="확인하지 못했어요">
            <p>{confirmError}</p>
          </Notice>
        </div>
      )}
      {reported && (
        <p role="status" className="text-sm text-ink">
          신고를 접수했어요. 신고가 쌓이면 운영자가 검토할 때까지 지도에서 내려요.
        </p>
      )}

      {reportOpen && (
        <ReportDialog
          spotId={spotId}
          onClose={() => setReportOpen(false)}
          onReported={() => {
            setReportOpen(false);
            setReported(true);
          }}
        />
      )}
    </div>
  );
}

function ReportDialog({
  spotId,
  onClose,
  onReported,
}: {
  spotId: number;
  onClose: () => void;
  onReported: () => void;
}) {
  const [reason, setReason] = useState<BakjiReportReason | null>(null);
  const [content, setContent] = useState('');
  const [reasonError, setReasonError] = useState<string | undefined>();
  const [contentError, setContentError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  useEffect(() => () => controllerRef.current?.abort(), []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    setReasonError(undefined);
    setContentError(undefined);
    setFormError(null);
    if (reason === null) {
      setReasonError('신고 사유를 골라 주세요.');
      return;
    }

    const request: BakjiProblemReportRequest = { reason };
    if (content.trim() !== '') request.content = content.trim();

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setSubmitting(true);
    try {
      await reportBakji(spotId, request, controller.signal);
      if (controller.signal.aborted) return;
      onReported();
    } catch (error) {
      if (controller.signal.aborted) return;
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const others: string[] = [];
      for (const { field, reason: message } of error.fieldErrors) {
        if (field === 'reason') setReasonError(message);
        else if (field === 'content') setContentError(message);
        else others.push(message);
      }
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    setFormError(toUserMessage(error));
  }

  return (
    <Dialog title="박지 문제 신고" onClose={onClose}>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">신고하면 되돌릴 수 없어요. 운영자가 확인할 수 있게 사유를 골라 주세요.</p>
        {formError && (
          <div role="alert">
            <Notice tone="danger" title="신고하지 못했어요">
              <p>{formError}</p>
            </Notice>
          </div>
        )}
        <ChoiceGroup
          legend="신고 사유"
          name="reason"
          options={REASON_OPTIONS}
          value={reason}
          onChange={setReason}
          error={reasonError}
        />
        <TextArea
          label="자세한 내용 (선택)"
          name="content"
          maxLength={REPORT_CONTENT_MAX}
          value={content}
          onChange={(event) => setContent(event.target.value)}
          error={contentError}
        />
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" variant="danger" loading={submitting}>
            신고하기
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
