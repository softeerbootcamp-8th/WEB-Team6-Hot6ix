import {
  useEffect,
  useRef,
  useState,
  type ElementType,
  type ReactNode,
} from 'react'

import { cn } from '@/lib/utils'

/**
 * 화면에 들어올 때 한 번 떠오르는 요소.
 *
 * 랜딩처럼 처음 마주하는 화면에서 정보가 순서대로 눈에 들어오게 한다.
 * 한 번 보이면 다시 감추지 않는다(스크롤을 오르내릴 때 깜빡이면 산만하다).
 *
 * `prefers-reduced-motion` 이면 애니메이션 없이 바로 보여준다.
 * `IntersectionObserver` 가 없는 환경에서도 마찬가지다.
 */
export function Reveal({
  as: Tag = 'div',
  delay = 0,
  className,
  children,
}: {
  as?: ElementType
  /** 순서대로 뜨게 할 때 쓰는 지연(ms) */
  delay?: number
  className?: string
  children: ReactNode
}) {
  const ref = useRef<HTMLElement>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const node = ref.current
    const reduced = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches

    if (!node || reduced || typeof IntersectionObserver === 'undefined') {
      setVisible(true)
      return
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return
        setVisible(true)
        // 한 번 뜬 뒤에는 관찰을 멈춘다.
        observer.disconnect()
      },
      // 요소가 조금 들어왔을 때 시작해야 스크롤과 어긋나 보이지 않는다.
      { threshold: 0.15, rootMargin: '0px 0px -40px 0px' },
    )

    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return (
    <Tag
      ref={ref}
      style={visible ? { transitionDelay: `${delay}ms` } : undefined}
      className={cn(
        'transition-[opacity,transform] duration-700 ease-[cubic-bezier(0.16,1,0.3,1)] motion-reduce:transition-none',
        visible ? 'translate-y-0 opacity-100' : 'translate-y-4 opacity-0',
        className,
      )}
    >
      {children}
    </Tag>
  )
}
