import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, FormEvent, ReactNode } from 'react';
import { createPost, getPost, putImage, requestImageUpload, updatePost } from '../../api/community';
import { ApiError } from '../../api/client';
import { toUserMessage } from '../../api/errors';
import { fetchSpotDetail } from '../../api/spots';
import type { CommunityPostDetail, CommunityPostUpdateRequest } from '../../api/types';
import { Link, navigate, useLocation } from '../../app/router';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { Icon } from '../../components/icons';
import { Notice } from '../../components/Notice';
import { AsideSection, HubLayout } from '../../components/HubLayout';
import { Skeleton } from '../../components/Skeleton';
import { TextArea } from '../../components/TextArea';
import { TextField } from '../../components/TextField';
import { useDelayedFlag } from '../../components/useDelayedFlag';
import { withNext } from '../member/nextPath';
import { useSession } from '../member/session';
import { parseSpotId, SECONDARY_LINK_CLASS } from './communityLabels';

// 서버 규칙과 같은 값이다.
const TITLE_MAX = 100;
const CONTENT_MAX = 10000;
const MAX_IMAGES = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp'];

type FormMode = 'create' | 'edit';

type CommunityPostFormPageProps = { mode: 'create' } | { mode: 'edit'; postId: number };

export function CommunityPostFormPage(props: CommunityPostFormPageProps) {
  const session = useSession();
  const { pathname, search } = useLocation();
  const showSkeleton = useDelayedFlag(session.status === 'loading');
  const editPostId = props.mode === 'edit' ? props.postId : null;

  useEffect(() => {
    document.title = props.mode === 'create' ? '글쓰기 · 피치맵' : '글 고치기 · 피치맵';
  }, [props.mode]);

  // 글쓰기는 로그인한 세션으로만 할 수 있다. 로그인한 뒤 이 화면으로 돌아오게 한다.
  useEffect(() => {
    if (session.status === 'anonymous') navigate(withNext('/login', pathname + search), { replace: true });
  }, [session.status, pathname, search]);

  const title = props.mode === 'create' ? '글쓰기' : '글 고치기';
  const layout = (children: ReactNode) => (
    <HubLayout
      title={title}
      description={props.mode === 'create' ? '다녀온 이야기, 장비, 궁금한 점을 나눠 주세요.' : undefined}
      breadcrumb={
        <Link
          to={editPostId === null ? '/community' : `/community/${editPostId}`}
          className="inline-flex min-h-11 items-center gap-1 text-forest underline underline-offset-2"
        >
          <Icon name="chevronLeft" size={16} />
          {editPostId === null ? '커뮤니티' : '글로 돌아가기'}
        </Link>
      }
      aside={<WriteAside />}
    >
      {children}
    </HubLayout>
  );

  if (session.status !== 'authenticated' || !session.me) {
    return layout(
      showSkeleton ? (
        <div className="rounded-control border border-contour bg-card p-5">
          <Skeleton className="h-40 w-full" />
        </div>
      ) : null,
    );
  }
  if (session.me.status === 'UNVERIFIED') {
    return layout(
      <div className="rounded-control border border-contour bg-card p-5">
        <Notice tone="warning" title="이메일 인증을 마치면 글을 쓸 수 있어요">
          <div className="flex flex-col gap-3">
            <p>받은 인증 코드를 입력하면 바로 쓸 수 있어요.</p>
            <div>
              <Link to="/verify-email" className={SECONDARY_LINK_CLASS}>
                이메일 인증하기
              </Link>
            </div>
          </div>
        </Notice>
      </div>,
    );
  }

  return layout(
    editPostId === null ? <CreateForm /> : <EditLoader postId={editPostId} myMemberId={session.me.memberId} />,
  );
}

function WriteAside() {
  return (
    <>
      <AsideSection title="글쓰기 안내">
        <ul className="list-disc pl-5">
          <li>
            동행 모집은{' '}
            <Link to="/basecamps" className="font-semibold text-forest underline underline-offset-2">
              베이스캠프
            </Link>
            에서 해요.
          </li>
          <li>송금을 요구하는 글은 신고 대상이에요.</li>
          <li>공원 안 야영을 권하지 않아요.</li>
          <li>전화번호나 주소 같은 개인정보는 쓰지 않아요.</li>
        </ul>
      </AsideSection>
      <AsideSection title="사진">
        <ul className="list-disc pl-5">
          <li>최대 5장까지 올려요.</li>
          <li>JPEG, PNG, WebP 파일을 장당 5MB까지 올릴 수 있어요.</li>
          <li>맨 앞 사진이 목록의 대표 사진이 돼요. 순서는 &apos;앞으로&apos; 버튼으로 바꿔요.</li>
        </ul>
      </AsideSection>
    </>
  );
}

function CreateForm() {
  const { search } = useLocation();
  const spotId = parseSpotId(new URLSearchParams(search).get('spotId'));
  return <PostForm mode="create" initialSpot={spotId === null ? null : { spotId, name: null }} />;
}

type EditLoadResult = { post: CommunityPostDetail } | { error: unknown };

function EditLoader({ postId, myMemberId }: { postId: number; myMemberId: number }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<EditLoadResult | null>(null);
  const showSkeleton = useDelayedFlag(result === null);

  useEffect(() => {
    const controller = new AbortController();
    getPost(postId, controller.signal).then(
      (post) => {
        if (!controller.signal.aborted) setResult({ post });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ error });
      },
    );
    return () => controller.abort();
  }, [postId, attempt]);

  const notMine = result !== null && 'post' in result && result.post.author.memberId !== myMemberId;
  // 작성자가 아니면 고칠 수 없으므로 글 상세로 돌려보낸다.
  useEffect(() => {
    if (notMine) navigate(`/community/${postId}`, { replace: true });
  }, [notMine, postId]);

  if (result === null || notMine) {
    return showSkeleton ? (
      <div className="rounded-control border border-contour bg-card p-5">
        <Skeleton className="h-64 w-full" />
      </div>
    ) : null;
  }
  if ('error' in result) {
    if (result.error instanceof ApiError && result.error.code === 'NOT_FOUND') {
      return (
        <div className="rounded-control border border-contour bg-card">
          <EmptyState
            title="글을 찾을 수 없어요"
            description="지워졌거나 볼 수 없는 글이에요."
            action={
              <Link to="/community" className={SECONDARY_LINK_CLASS}>
                글 목록으로
              </Link>
            }
          />
        </div>
      );
    }
    return (
      <div className="flex flex-col gap-3">
        <Notice tone="danger" title="글을 불러오지 못했어요">
          {toUserMessage(result.error)}
        </Notice>
        <div>
          <Button
            variant="secondary"
            onClick={() => {
              setResult(null);
              setAttempt((value) => value + 1);
            }}
          >
            다시 불러오기
          </Button>
        </div>
      </div>
    );
  }
  return <PostForm mode="edit" post={result.post} initialSpot={result.post.spot ?? null} />;
}

type ImageItem = {
  key: string;
  // 서버에 올린 뒤에 받는다. 기존 이미지는 처음부터 있다.
  imageId?: number;
  // 기존 이미지의 조회 URL
  url?: string;
  file?: File;
  status: 'uploading' | 'done' | 'failed';
  error?: string;
};

type LinkedSpot = { spotId: number; name: string | null };

type FormField = 'title' | 'content' | 'imageIds';
type FormFieldErrors = Partial<Record<FormField, string>>;

function isFormField(field: string): field is FormField {
  return field === 'title' || field === 'content' || field === 'imageIds';
}

type PostFormProps = { initialSpot: LinkedSpot | null } & ({ mode: 'create' } | { mode: 'edit'; post: CommunityPostDetail });

function PostForm(props: PostFormProps) {
  const mode: FormMode = props.mode;
  const initialPost = props.mode === 'edit' ? props.post : null;
  const [title, setTitle] = useState(initialPost?.title ?? '');
  const [content, setContent] = useState(initialPost?.content ?? '');
  const [spot, setSpot] = useState<LinkedSpot | null>(props.initialSpot);
  const [images, setImages] = useState<ImageItem[]>(() =>
    (initialPost?.images ?? []).map((image) => ({
      key: `saved-${image.imageId}`,
      imageId: image.imageId,
      url: image.url,
      status: 'done',
    })),
  );
  const [imageMessages, setImageMessages] = useState<string[]>([]);
  const [fieldErrors, setFieldErrors] = useState<FormFieldErrors>({});
  const [formError, setFormError] = useState<ApiError | string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const nextKey = useRef(0);

  // 새로 연결한 장소(주소의 spotId)는 이름을 모르므로 한 번 받아 칩에 보여 준다. 받지 못하면 이름 없이 둔다.
  const unnamedSpotId = spot !== null && spot.name === null ? spot.spotId : null;
  useEffect(() => {
    if (unnamedSpotId === null) return;
    const controller = new AbortController();
    fetchSpotDetail(unnamedSpotId, controller.signal).then(
      (detail) => {
        if (controller.signal.aborted) return;
        setSpot((previous) =>
          previous !== null && previous.spotId === unnamedSpotId ? { spotId: unnamedSpotId, name: detail.name } : previous,
        );
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [unnamedSpotId]);

  const uploading = images.some((image) => image.status === 'uploading');
  const failed = images.some((image) => image.status === 'failed');
  const slotsLeft = MAX_IMAGES - images.length;

  function patchImage(key: string, patch: Partial<ImageItem>) {
    setImages((previous) => previous.map((image) => (image.key === key ? { ...image, ...patch } : image)));
  }

  // 올리는 도중에 이미지를 뺐다면 patchImage가 아무것도 바꾸지 않는다.
  async function upload(key: string, file: File) {
    patchImage(key, { status: 'uploading', error: undefined });
    try {
      const target = await requestImageUpload({ contentType: file.type, sizeBytes: file.size });
      await putImage(target, file);
      patchImage(key, { status: 'done', imageId: target.imageId });
    } catch (error) {
      patchImage(key, { status: 'failed', error: toUserMessage(error) });
    }
  }

  function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    // 같은 파일을 다시 고를 수 있게 입력을 비운다.
    event.target.value = '';
    const messages: string[] = [];
    const accepted: ImageItem[] = [];
    let room = slotsLeft;
    for (const file of files) {
      if (!IMAGE_TYPES.includes(file.type)) {
        messages.push(`${file.name}: JPG, PNG, WebP 파일만 올릴 수 있어요.`);
      } else if (file.size > MAX_IMAGE_BYTES) {
        messages.push(`${file.name}: 5MB 이하 파일만 올릴 수 있어요. 더 작은 파일을 골라 주세요.`);
      } else if (file.size === 0) {
        messages.push(`${file.name}: 비어 있는 파일이에요. 다른 파일을 골라 주세요.`);
      } else if (room <= 0) {
        messages.push(`${file.name}: 이미지는 ${MAX_IMAGES}장까지 올릴 수 있어요. 다른 이미지를 빼고 올려 주세요.`);
      } else {
        room -= 1;
        accepted.push({ key: `new-${nextKey.current++}`, file, status: 'uploading' });
      }
    }
    setImageMessages(messages);
    if (accepted.length === 0) return;
    setImages((previous) => [...previous, ...accepted]);
    for (const item of accepted) {
      if (item.file) void upload(item.key, item.file);
    }
  }

  function moveForward(index: number) {
    setImages((previous) => {
      if (index <= 0 || index >= previous.length) return previous;
      const next = [...previous];
      const moved = next[index];
      const before = next[index - 1];
      if (!moved || !before) return previous;
      next[index - 1] = moved;
      next[index] = before;
      return next;
    });
  }

  function removeImage(key: string) {
    setImages((previous) => previous.filter((image) => image.key !== key));
    setImageMessages([]);
  }

  function validate(): FormFieldErrors {
    const errors: FormFieldErrors = {};
    if (title.trim() === '') errors.title = '제목을 적어 주세요.';
    else if (title.length > TITLE_MAX) errors.title = `${TITLE_MAX}자 이하로 적어 주세요.`;
    if (content.trim() === '') errors.content = '내용을 적어 주세요.';
    else if (content.length > CONTENT_MAX) errors.content = `${CONTENT_MAX.toLocaleString()}자 이하로 적어 주세요.`;
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || uploading || failed) return;
    setFormError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const imageIds = images.flatMap((image) => (image.imageId === undefined ? [] : [image.imageId]));
    setSubmitting(true);
    try {
      if (props.mode === 'create') {
        const created = await createPost({
          title: title.trim(),
          content: content.trim(),
          spotId: spot?.spotId,
          imageIds,
        });
        navigate(`/community/${created.postId}`);
        return;
      }
      const post = props.post;
      const changes: CommunityPostUpdateRequest = {};
      if (title.trim() !== post.title) changes.title = title.trim();
      if (content.trim() !== post.content) changes.content = content.trim();
      if ((spot?.spotId ?? null) !== (post.spot?.spotId ?? null)) changes.spotId = spot?.spotId ?? null;
      const savedIds = post.images.map((image) => image.imageId);
      if (imageIds.length !== savedIds.length || imageIds.some((id, index) => id !== savedIds[index])) {
        changes.imageIds = imageIds;
      }
      if (Object.keys(changes).length > 0) await updatePost(post.postId, changes);
      navigate(`/community/${post.postId}`);
    } catch (error) {
      setSubmitting(false);
      handleError(error);
    }
  }

  function handleError(error: unknown) {
    if (error instanceof ApiError && error.code === 'INVALID_INPUT' && error.fieldErrors.length > 0) {
      const errors: FormFieldErrors = {};
      const others: string[] = [];
      for (const { field, reason } of error.fieldErrors) {
        if (isFormField(field)) errors[field] = reason;
        else others.push(reason);
      }
      setFieldErrors(errors);
      if (others.length > 0) setFormError(others.join(' '));
      return;
    }
    setFormError(error instanceof ApiError ? error : toUserMessage(error));
  }

  let submitReason: string | undefined;
  if (uploading) submitReason = '이미지를 올리는 중이에요. 끝나면 저장할 수 있어요.';
  else if (failed) submitReason = '올리지 못한 이미지를 다시 시도하거나 빼 주세요.';

  const spotLabel = spot === null ? null : spot.name === null ? '선택한 장소' : spot.name;
  const cancelTarget = props.mode === 'edit' ? `/community/${props.post.postId}` : '/community';

  return (
    <form
      noValidate
      onSubmit={handleSubmit}
      aria-label={mode === 'create' ? '글쓰기' : '글 고치기'}
      className="divide-y divide-contour rounded-control border border-contour bg-card"
    >
      {formError && (
        <div role="alert" className="px-5 py-5">
          <Notice tone="danger" title={mode === 'create' ? '글을 올리지 못했어요' : '글을 고치지 못했어요'}>
            <p>{typeof formError === 'string' ? formError : formError.message}</p>
            {typeof formError !== 'string' && formError.status >= 500 && formError.traceId && (
              <p className="mt-1 font-mono text-xs text-ink-muted">문의 번호 {formError.traceId}</p>
            )}
          </Notice>
        </div>
      )}

      <div className="flex flex-col gap-3 px-5 py-5">
        <div className="flex flex-col gap-1">
          <TextField
            label="제목"
            name="title"
            maxLength={TITLE_MAX}
            value={title}
            disabled={submitting}
            onChange={(event) => setTitle(event.target.value)}
            error={fieldErrors.title}
          />
          <p className="text-right font-mono text-xs tabular-nums text-ink-muted">
            {title.length}/{TITLE_MAX}
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-3 px-5 py-5">
        <TextArea
          label="본문"
          name="content"
          rows={12}
          maxLength={CONTENT_MAX}
          value={content}
          disabled={submitting}
          onChange={(event) => setContent(event.target.value)}
          error={fieldErrors.content}
        />
      </div>

      <ImageField
        images={images}
        messages={imageMessages}
        error={fieldErrors.imageIds}
        slotsLeft={slotsLeft}
        disabled={submitting}
        onFiles={handleFiles}
        onRetry={(image) => image.file && void upload(image.key, image.file)}
        onMoveForward={moveForward}
        onRemove={removeImage}
      />

      <div className="flex flex-col gap-3 px-5 py-5">
        <p className="text-sm font-medium text-ink">연결한 장소</p>
        {spotLabel === null ? (
          <p className="text-sm text-ink-muted">
            지도 장소 상세에서 &apos;이 장소로 글쓰기&apos;를 누르면 장소를 연결할 수 있어요.
          </p>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="inline-flex min-h-11 items-center gap-2 text-base text-ink">
              <Icon name="map" size={18} className="shrink-0 text-ink-muted" />
              {spotLabel}
            </span>
            <Button
              variant="ghost"
              aria-label={`${spotLabel} 연결 해제`}
              disabled={submitting}
              onClick={() => setSpot(null)}
            >
              연결 해제
            </Button>
          </div>
        )}
      </div>

      <p className="flex items-center gap-2 px-5 py-3 text-sm text-ink-muted">
        <Icon name="info" size={16} className="shrink-0" />
        동행은 베이스캠프에서 구하고, 송금 요구는 신고해 주세요.
      </p>

      <div className="sticky bottom-(--bottom-nav-h) flex flex-wrap items-start justify-end gap-2 bg-card px-5 py-4 lg:static">
        <Link to={cancelTarget} className={SECONDARY_LINK_CLASS}>
          취소
        </Link>
        <Button type="submit" loading={submitting} disabled={uploading || failed} disabledReason={submitReason}>
          {mode === 'create' ? '올리기' : '고치기'}
        </Button>
      </div>
    </form>
  );
}

type ImageFieldProps = {
  images: ImageItem[];
  messages: string[];
  error?: string;
  slotsLeft: number;
  disabled: boolean;
  onFiles: (event: ChangeEvent<HTMLInputElement>) => void;
  onRetry: (image: ImageItem) => void;
  onMoveForward: (index: number) => void;
  onRemove: (key: string) => void;
};

function ImageField({ images, messages, error, slotsLeft, disabled, onFiles, onRetry, onMoveForward, onRemove }: ImageFieldProps) {
  const full = slotsLeft <= 0;
  return (
    <fieldset className="flex min-w-0 flex-col gap-3 px-5 py-5">
      <legend className="float-left mb-3 w-full text-sm font-medium text-ink">사진 (최대 5장)</legend>
      <p className="text-sm text-ink-muted">JPEG·PNG·WebP, 장당 5MB까지예요.</p>

      {messages.length > 0 && (
        <div role="alert">
          <Notice tone="warning" title="올리지 못한 파일이 있어요">
            <ul className="list-disc pl-5">
              {messages.map((message) => (
                <li key={message} className="break-words">
                  {message}
                </li>
              ))}
            </ul>
          </Notice>
        </div>
      )}

      <ul className="clear-both grid grid-cols-3 gap-2 sm:grid-cols-5">
        {images.map((image, index) => (
          <li key={image.key} className="flex min-w-0 flex-col gap-1">
            <ImagePreview file={image.file} url={image.url} alt={`올린 이미지 ${index + 1}`} />
            <p className="text-xs text-ink-muted">
              {image.status === 'uploading' && '올리는 중이에요'}
              {image.status === 'done' && <span className="text-forest-deep">올렸어요</span>}
              {image.status === 'failed' && <span className="text-danger">올리지 못했어요</span>}
            </p>
            {image.status === 'failed' && image.error && (
              <p role="alert" className="break-words text-xs text-danger">
                {image.error}
              </p>
            )}
            <div className="flex flex-wrap">
              {image.status === 'failed' && (
                <Button variant="secondary" onClick={() => onRetry(image)} disabled={disabled} className="px-2 text-sm">
                  다시 시도
                </Button>
              )}
              {index > 0 && (
                <Button
                  variant="ghost"
                  aria-label={`이미지 ${index + 1}번 앞으로 옮기기`}
                  onClick={() => onMoveForward(index)}
                  disabled={disabled}
                  className="px-2 text-sm"
                >
                  <Icon name="arrowUp" size={16} />
                  앞으로
                </Button>
              )}
              <Button
                variant="ghost"
                aria-label={`이미지 ${index + 1}번 빼기`}
                className="px-2 text-sm text-danger"
                onClick={() => onRemove(image.key)}
                disabled={disabled}
              >
                빼기
              </Button>
            </div>
          </li>
        ))}
        {!full && (
          <li className="relative flex min-w-0">
            <input
              id="community-image-input"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              disabled={disabled}
              onChange={onFiles}
              className="peer sr-only"
            />
            <label
              htmlFor="community-image-input"
              className={[
                'flex aspect-square w-full flex-col items-center justify-center gap-1 rounded-control border border-dashed border-ink-subtle p-1 text-center text-sm peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-sea',
                disabled ? 'cursor-not-allowed bg-paper-deep text-ink-muted' : 'cursor-pointer bg-card text-ink hover:bg-paper-deep',
              ].join(' ')}
            >
              <Icon name="image" size={24} />
              <span>사진 추가</span>
              <span className="font-mono text-xs tabular-nums text-ink-muted">
                {images.length}/{MAX_IMAGES}
              </span>
            </label>
          </li>
        )}
      </ul>

      {error && (
        <p role="alert" className="flex items-start gap-1 text-sm text-danger">
          <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </fieldset>
  );
}

// 올리는 파일은 브라우저 안에서만 쓰는 임시 주소로 미리 보여 준다. 임시 주소를 만들고 지우는 일을 같은 effect에 둬야
// 개발 모드에서 effect가 두 번 돌아도 주소가 새지 않는다. 주소는 상태를 거치지 않고 img 요소에 바로 넣는다.
function ImagePreview({ file, url, alt }: { file?: File; url?: string; alt: string }): ReactNode {
  const ref = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const image = ref.current;
    if (!file || !image) return;
    const created = URL.createObjectURL(file);
    image.src = created;
    return () => URL.revokeObjectURL(created);
  }, [file]);

  const box = 'aspect-square w-full rounded-control border border-contour bg-paper-deep object-cover';
  return <img ref={ref} src={file ? undefined : url} alt={alt} className={box} />;
}
