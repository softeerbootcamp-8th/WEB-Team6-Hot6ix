import { Minus, Plus } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * 폼 필드 공용 컴포넌트.
 *
 * Figma 판매자 프로필 등록(WEB-03)·상품 등록(WEB-06)·경매방 생성(WEB-08)이
 * 전부 같은 모양을 쓴다 — 라벨 14 Bold, 입력 52 높이에 반경 14,
 * 에러는 그 아래 12 빨강. 화면마다 다시 조립하지 말고 이걸 쓴다.
 *
 * `aria-invalid` 와 `aria-describedby` 연결을 컴포넌트가 책임진다.
 * 손으로 id 를 이어붙이다 빠뜨리는 실수를 막으려는 것이다.
 */

const CONTROL =
  'ease-soft w-full rounded-[14px] border bg-card px-4 text-[14px] font-medium text-foreground transition-colors duration-150 outline-none placeholder:font-normal placeholder:text-neutral-muted focus-visible:border-brand-400 aria-invalid:border-live disabled:cursor-not-allowed disabled:bg-surface-subtle disabled:text-neutral-muted'

interface FieldShellProps {
  label: string
  /** 값이 반드시 있어야 하는 칸. 라벨 옆에 점을 찍는다. */
  required?: boolean
  /** 평소에 보여주는 도움말. 에러가 있으면 에러가 대신 나온다. */
  hint?: ReactNode
  error?: string
  children: (props: {
    id: string
    'aria-invalid': boolean
    'aria-describedby': string | undefined
    className: string
  }) => ReactNode
}

/**
 * 라벨 + 컨트롤 + 도움말/에러 한 벌.
 *
 * 컨트롤은 render prop 으로 받는다. input·textarea·select 어느 것이 와도
 * 접근성 속성과 클래스를 똑같이 붙여 주기 위해서다.
 */
export function Field({
  label,
  required,
  hint,
  error,
  children,
}: FieldShellProps) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined

  return (
    <div>
      <label
        htmlFor={id}
        className="flex items-center gap-1 text-[14px] font-bold text-foreground"
      >
        {label}
        {required && (
          <span aria-hidden className="text-live">
            *
          </span>
        )}
      </label>

      <div className="mt-2.5">
        {children({
          id,
          'aria-invalid': error !== undefined,
          'aria-describedby': describedBy,
          className: CONTROL,
        })}
      </div>

      {error ? (
        <p
          id={`${id}-error`}
          className="mt-2 text-[12px] font-medium text-live"
        >
          {error}
        </p>
      ) : hint ? (
        <p
          id={`${id}-hint`}
          className="mt-2 text-[12px] font-medium text-neutral-muted"
        >
          {hint}
        </p>
      ) : null}
    </div>
  )
}

type BaseProps = Omit<FieldShellProps, 'children'>

/** 한 줄 입력. Figma 기준 높이 52. */
export function TextField({
  label,
  required,
  hint,
  error,
  className,
  ...props
}: BaseProps &
  Omit<React.ComponentProps<'input'>, 'id' | 'className'> & {
    className?: string
  }) {
  return (
    <Field label={label} required={required} hint={hint} error={error}>
      {(field) => (
        <input
          {...props}
          {...field}
          className={cn('h-13', field.className, className)}
        />
      )}
    </Field>
  )
}

/**
 * 숫자 입력.
 *
 * 단위(`원`·`분`)를 **입력창 안이 아니라 오른쪽 밖에** 둔다.
 * 값에 단위를 섞어 넣으면 커서가 단위 뒤로 가거나 지우기 애매해서,
 * 어디까지가 입력값인지 헷갈린다.
 *
 * `steps` 를 주면 −/+ 버튼이 붙는다. 두 개 이상이면 **−/+ 가 한 번에 움직일
 * 폭**을 고르는 칩이 함께 붙는다.
 *
 * `quickAdd` 는 다른 방식이다. 칩이 폭을 고르는 게 아니라 **누르는 즉시 그만큼
 * 더한다**(`+1,000원`). 목표 금액이 정해져 있어 한 번에 올리고 싶은 입찰 단위용이다.
 * `steps` 와 같이 쓰지 않는다 — 같은 칩 줄이 두 가지 뜻을 가지면 헷갈린다.
 *
 * `max` 는 −/+ 버튼만 가둔다. **직접 입력은 막지 않는다.** 타이핑하는 값을 조용히
 * 되돌리면 왜 바뀌었는지 알 수 없어서, 넘긴 값은 그대로 두고 부르는 쪽이 `error` 로
 * 알리게 한다.
 */
export function NumberField({
  label,
  required,
  hint,
  error,
  unit,
  value,
  onValueChange,
  steps,
  quickAdd,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
  disabled,
  ...props
}: BaseProps &
  Omit<
    React.ComponentProps<'input'>,
    'id' | 'className' | 'value' | 'onChange' | 'type' | 'step' | 'min' | 'max'
  > & {
    unit: string
    value: number
    onValueChange: (next: number) => void
    /** −/+ 가 움직일 폭. 두 개 이상이면 고르는 칩이 붙는다. */
    steps?: number[]
    /** 누르면 그만큼 바로 더하는 칩. `steps` 대신 쓴다(−/+ 버튼은 안 붙는다). */
    quickAdd?: number[]
    min?: number
    max?: number
  }) {
  const [stepIndex, setStepIndex] = useState(0)
  const step = quickAdd ? undefined : (steps?.[stepIndex] ?? steps?.[0])

  const stepBy = (delta: number) =>
    onValueChange(Math.min(max, Math.max(min, value + delta)))

  return (
    <Field label={label} required={required} hint={hint} error={error}>
      {(field) => (
        <div>
          <div className="flex items-center gap-2">
            {step !== undefined && (
              <StepButton
                sign="minus"
                label={`${step.toLocaleString('ko-KR')}${unit} 줄이기`}
                disabled={disabled || value - step < min}
                onClick={() => stepBy(-step)}
              />
            )}

            <input
              {...props}
              {...field}
              disabled={disabled}
              inputMode="numeric"
              value={value.toLocaleString('ko-KR')}
              onChange={(event) =>
                onValueChange(
                  Number(event.target.value.replace(/\D/g, '')) || 0,
                )
              }
              className={cn(
                'h-13 min-w-0 flex-1 text-center font-semibold tabular-nums',
                field.className,
              )}
            />

            {step !== undefined && (
              <StepButton
                sign="plus"
                label={`${step.toLocaleString('ko-KR')}${unit} 늘리기`}
                disabled={disabled || value + step > max}
                onClick={() => stepBy(step)}
              />
            )}

            <span className="w-5 shrink-0 text-[14px] font-semibold text-neutral-tertiary">
              {unit}
            </span>
          </div>

          {quickAdd && quickAdd.length > 0 && (
            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              <span className="text-[11px] font-medium text-neutral-muted">
                빠른 입력
              </span>
              {quickAdd.map((amount) => (
                <button
                  key={amount}
                  type="button"
                  disabled={disabled}
                  onClick={() => stepBy(amount)}
                  className="ease-soft h-8 rounded-lg border bg-card px-3 text-[12px] font-bold text-neutral-tertiary transition-all duration-150 hover:border-border-strong active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  +{amount.toLocaleString('ko-KR')}
                  {unit}
                </button>
              ))}
            </div>
          )}

          {!quickAdd && steps && steps.length > 1 && (
            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              <span className="text-[11px] font-medium text-neutral-muted">
                증감 단위
              </span>
              {steps.map((candidate, index) => (
                <button
                  key={candidate}
                  type="button"
                  disabled={disabled}
                  onClick={() => setStepIndex(index)}
                  aria-pressed={index === stepIndex}
                  className={cn(
                    'ease-soft h-8 rounded-lg border px-3 text-[12px] font-bold transition-all duration-150 active:scale-95 disabled:cursor-not-allowed disabled:opacity-40',
                    index === stepIndex
                      ? 'border-brand-300 bg-brand-50 text-brand-500'
                      : 'bg-card text-neutral-tertiary hover:border-border-strong',
                  )}
                >
                  {candidate.toLocaleString('ko-KR')}
                  {unit}씩
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </Field>
  )
}

function StepButton({
  sign,
  label,
  disabled,
  onClick,
}: {
  sign: 'minus' | 'plus'
  label: string
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="ease-soft flex size-13 shrink-0 items-center justify-center rounded-[14px] border bg-card text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-90 disabled:cursor-not-allowed disabled:opacity-40"
    >
      {sign === 'minus' ? (
        <Minus aria-hidden className="size-4" />
      ) : (
        <Plus aria-hidden className="size-4" />
      )}
    </button>
  )
}

/** 여러 줄 입력. 높이는 화면마다 달라서 `className` 으로 받는다. */
export function TextAreaField({
  label,
  required,
  hint,
  error,
  className,
  ...props
}: BaseProps &
  Omit<React.ComponentProps<'textarea'>, 'id' | 'className'> & {
    className?: string
  }) {
  return (
    <Field label={label} required={required} hint={hint} error={error}>
      {(field) => (
        <textarea
          {...props}
          {...field}
          className={cn(
            'h-[92px] resize-none py-4',
            field.className,
            className,
          )}
        />
      )}
    </Field>
  )
}

/** 선택. 값이 비어 있으면 placeholder 처럼 흐리게 보인다. */
export function SelectField({
  label,
  required,
  hint,
  error,
  className,
  children,
  ...props
}: BaseProps &
  Omit<React.ComponentProps<'select'>, 'id' | 'className'> & {
    className?: string
  }) {
  return (
    <Field label={label} required={required} hint={hint} error={error}>
      {(field) => (
        <select
          {...props}
          {...field}
          className={cn(
            'h-13',
            field.className,
            !props.value && 'text-neutral-muted',
            className,
          )}
        >
          {children}
        </select>
      )}
    </Field>
  )
}
