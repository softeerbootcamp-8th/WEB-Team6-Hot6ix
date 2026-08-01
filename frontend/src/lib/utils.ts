import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

/**
 * Figma 텍스트 스타일 토큰 (`globals.css` 의 `--text-*`).
 *
 * tailwind-merge 는 기본 스케일만 알기 때문에 `text-badge` 같은 우리 토큰을
 * **글자 색으로 오해**한다. 그러면 `cn('text-badge', 'text-live')` 처럼 크기와
 * 색을 함께 쓸 때 크기 쪽이 충돌로 판정돼 조용히 사라진다.
 * (실제로 LIVE 배지가 11px 대신 브라우저 기본 16px 로 커져 있었다.)
 *
 * 어떤 이름이 글자 크기인지 알려줘서 색과 나란히 살아남게 한다.
 */
const TEXT_SIZE_TOKENS = [
  'logo',
  'price',
  'room-title',
  'card-title',
  'body-strong',
  'body',
  'label',
  'caption',
  'timer',
  'badge',
] as const

const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      'font-size': [{ text: [...TEXT_SIZE_TOKENS] }],
    },
  },
})

/** Tailwind 클래스를 조건부로 합치고 충돌을 병합한다. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
