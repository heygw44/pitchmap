import type { SVGProps } from 'react';
import { ICON_PATHS } from './iconPaths';
import type { IconName } from './iconPaths';

type IconProps = Omit<SVGProps<SVGSVGElement>, 'children' | 'dangerouslySetInnerHTML'> & {
  name: IconName;
  size?: number;
  title?: string;
};

function escapeText(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// title이 없으면 장식으로 보고 스크린 리더가 건너뛰게 한다. 뜻을 전하는 아이콘에만 title을 넘긴다.
export function Icon({ name, size = 20, title, ...svgProps }: IconProps) {
  const markup = title ? `<title>${escapeText(title)}</title>${ICON_PATHS[name]}` : ICON_PATHS[name];
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      focusable="false"
      {...(title ? { role: 'img' } : { 'aria-hidden': true })}
      {...svgProps}
      dangerouslySetInnerHTML={{ __html: markup }}
    />
  );
}
