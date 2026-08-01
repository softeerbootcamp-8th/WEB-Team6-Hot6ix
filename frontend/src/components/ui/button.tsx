import * as React from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/utils'

/**
 * 버튼.
 *
 * shadcn 기본 변형 위에 **Figma 에서 반복되는 4가지**를 얹었다.
 * `brand` / `brandOutline` / `danger` / `dangerOutline` 이다.
 * 화면에서 버튼 스타일을 손으로 조립하지 말고 이 변형을 쓴다.
 *
 * 눌림감은 전 화면 공통 규약(`ease-soft` + `active:scale`)을 따른다.
 */
const buttonVariants = cva(
  "ease-soft inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-all duration-150 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4 shrink-0 outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
  {
    variants: {
      variant: {
        // 파란 채움 버튼의 글자는 언제나 흰색이다. 토큰을 거치면 다크 모드나
        // 비활성 상태에서 회색으로 보이는 일이 생긴다.
        default: 'bg-brand-500 text-white hover:opacity-90',
        destructive:
          'bg-destructive text-destructive-foreground hover:bg-destructive/90',
        outline:
          'border border-input bg-background hover:bg-accent hover:text-accent-foreground',
        secondary:
          'bg-secondary text-secondary-foreground hover:bg-secondary/80',
        ghost: 'hover:bg-accent hover:text-accent-foreground',
        link: 'text-primary underline-offset-4 hover:underline',

        /** Figma 기본 CTA — 브랜드 채움 */
        brand:
          // 비활성일 때 반투명으로 두면 흰 글자가 회색처럼 흐려 보인다.
          // 배경만 중립색으로 바꾸고 글자는 흰색을 유지한다.
          'bg-brand-500 text-white hover:opacity-90 active:scale-[0.98] disabled:bg-border-strong disabled:text-white disabled:opacity-100 disabled:active:scale-100',
        /** Figma 보조 버튼 — 브랜드 테두리 */
        brandOutline:
          'border border-brand-200 bg-card text-brand-500 hover:bg-brand-50 active:scale-[0.98] disabled:active:scale-100',
        /** 되돌릴 수 없는 동작 — 빨강 채움 */
        danger:
          'bg-live text-white hover:opacity-90 active:scale-[0.98] disabled:active:scale-100',
        /** 되돌릴 수 없는 동작 — 빨강 테두리 */
        dangerOutline:
          'border border-live/40 bg-card text-live hover:bg-live-surface active:scale-[0.98] disabled:active:scale-100',
      },
      size: {
        default: 'h-9 px-4 py-2 has-[>svg]:px-3',
        sm: 'h-8 rounded-md gap-1.5 px-3 has-[>svg]:px-2.5',
        lg: 'h-10 rounded-md px-6 has-[>svg]:px-4',
        icon: 'size-9',
        // Figma 폼·목록 버튼 — 높이 44 / 52 · 라운딩 14
        field: 'h-11 rounded-[14px] px-4 text-[15px] font-medium',
        form: 'h-13 rounded-[14px] px-5 text-[15px] font-bold',
        // 메인 CTA — Figma 기준 높이 56 · 라운딩 14
        cta: 'h-14 w-full rounded-2xl px-6 text-card-title font-bold',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }) {
  const Comp = asChild ? Slot : 'button'

  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants }
