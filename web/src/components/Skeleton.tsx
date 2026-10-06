type SkeletonProps = {
  className?: string;
};

// 크기는 쓰는 쪽이 className으로 정한다. 300ms보다 오래 걸리는 로딩에만 보이도록 useDelayedFlag와 함께 쓴다.
export function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={['animate-pulse rounded-control bg-paper-deep motion-reduce:animate-none', className ?? '']
        .filter(Boolean)
        .join(' ')}
    />
  );
}
