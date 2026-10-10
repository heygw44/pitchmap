import { useState } from 'react';
import { reportComment, reportPost } from '../../api/community';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import type { CommunityReportReason } from '../../api/types';
import { Button } from '../../components/Button';
import { ChoiceGroup } from '../../components/ChoiceGroup';
import { Dialog } from '../../components/Dialog';
import { Notice } from '../../components/Notice';
import { TextArea } from '../../components/TextArea';
import { REPORT_REASON_OPTIONS } from './communityLabels';

const CONTENT_MAX = 1000;

export type CommunityReportTarget = { type: 'post' | 'comment'; id: number };

type CommunityReportDialogProps = {
  target: CommunityReportTarget;
  onClose: () => void;
};

// 글이나 댓글을 신고한다. 같은 대상을 다시 신고할 수 있는지는 서버가 판단한다.
export function CommunityReportDialog({ target, onClose }: CommunityReportDialogProps) {
  const [reason, setReason] = useState<CommunityReportReason | null>(null);
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<ApiError | string | null>(null);
  const [reasonError, setReasonError] = useState<string | undefined>();
  const [contentError, setContentError] = useState<string | undefined>();

  const targetLabel = target.type === 'post' ? '글' : '댓글';
  const title = `${targetLabel} 신고`;

  async function submit() {
    if (reason === null || submitting) return;
    const trimmed = content.trim();
    const request = { reason, content: trimmed === '' ? undefined : trimmed };
    setSubmitting(true);
    setError(null);
    setReasonError(undefined);
    setContentError(undefined);
    try {
      if (target.type === 'post') await reportPost(target.id, request);
      else await reportComment(target.id, request);
      setDone(true);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'COMMUNITY_ALREADY_REPORTED') {
        setError('이미 신고했어요. 처리 결과를 기다려 주세요.');
      } else if (caught instanceof ApiError && caught.fieldErrors.length > 0) {
        const reasonItem = caught.fieldErrors.find((item) => item.field === 'reason');
        const contentItem = caught.fieldErrors.find((item) => item.field === 'content');
        setReasonError(reasonItem?.reason);
        setContentError(contentItem?.reason);
        if (!reasonItem && !contentItem) setError(caught);
      } else {
        setError(caught instanceof ApiError ? caught : toUserMessage(caught));
      }
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <Dialog title={title} onClose={onClose}>
        <div className="flex flex-col gap-4">
          <p role="status" className="text-base font-semibold text-forest-deep">
            신고를 접수했어요
          </p>
          <p className="text-sm text-ink-muted">관리자가 검토한 뒤 조치해요. 알려 주셔서 고마워요.</p>
          <div className="flex justify-end">
            <Button onClick={onClose}>닫기</Button>
          </div>
        </div>
      </Dialog>
    );
  }

  return (
    <Dialog title={title} onClose={() => !submitting && onClose()}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-muted">신고한 {targetLabel}은 관리자가 검토해요. 신고는 취소할 수 없어요.</p>
        {error && (
          <div role="alert">
            <Notice tone="danger" title="신고를 접수하지 못했어요">
              <p>{typeof error === 'string' ? error : error.message}</p>
              {typeof error !== 'string' && error.status >= 500 && error.traceId && (
                <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {error.traceId}</p>
              )}
            </Notice>
          </div>
        )}
        <ChoiceGroup
          legend="신고 사유"
          name="communityReportReason"
          options={REPORT_REASON_OPTIONS}
          value={reason}
          onChange={setReason}
          error={reasonError}
          disabled={submitting}
        />
        <TextArea
          label="신고 내용 (선택)"
          name="content"
          maxLength={CONTENT_MAX}
          value={content}
          disabled={submitting}
          error={contentError}
          hint="필요하면 어떤 점이 문제인지 적어 주세요."
          onChange={(event) => setContent(event.target.value)}
        />
        <div className="flex flex-wrap items-start justify-end gap-2">
          <Button variant="secondary" disabled={submitting} onClick={onClose}>
            닫기
          </Button>
          <Button
            variant="danger"
            loading={submitting}
            disabled={reason === null}
            disabledReason={reason === null ? '신고 사유를 골라 주세요.' : undefined}
            onClick={() => void submit()}
          >
            신고하기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
