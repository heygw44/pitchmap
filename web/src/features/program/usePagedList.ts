import { useCallback, useEffect, useRef, useState } from 'react';
import { toUserMessage } from '../../api/errors';
import type { Page } from '../../api/types';

type ListState<T, F> = { filter: F; items: T[]; page: number; hasNext: boolean; error: string | null };

type Fetcher<T, F> = (filter: F, page: number, signal?: AbortSignal) => Promise<Page<T>>;

// 필터가 바뀌면 처음부터 다시 받고, loadMore는 다음 페이지를 이어 붙인다.
// current가 null이면 현재 필터의 첫 응답을 기다리는 중이다.
export function usePagedList<T, F>(enabled: boolean, filter: F, fetcher: Fetcher<T, F>) {
  const [state, setState] = useState<ListState<T, F> | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    fetcherRef.current(filter, 0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          setState({ filter, items: page.content, page: page.page, hasNext: page.hasNext, error: null });
        }
      },
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          setState({ filter, items: [], page: 0, hasNext: false, error: toUserMessage(caught) });
        }
      },
    );
    return () => controller.abort();
  }, [enabled, filter, attempt]);

  const current = state !== null && state.filter === filter ? state : null;

  const loadMore = useCallback(async () => {
    if (!current || loadingMore) return;
    setLoadingMore(true);
    try {
      const next = await fetcherRef.current(filter, current.page + 1);
      setState((previous) =>
        previous !== null && previous.filter === filter
          ? { ...previous, items: [...previous.items, ...next.content], page: next.page, hasNext: next.hasNext, error: null }
          : previous,
      );
    } catch (caught) {
      setState((previous) =>
        previous !== null && previous.filter === filter ? { ...previous, error: toUserMessage(caught) } : previous,
      );
    } finally {
      setLoadingMore(false);
    }
  }, [current, filter, loadingMore]);

  const retry = useCallback(() => {
    setState(null);
    setAttempt((value) => value + 1);
  }, []);

  return { current, loadingMore, loadMore, retry };
}
