/**
 * 이벤트 아이콘.
 *
 * Figma `ic_ev*` 프레임에서 그대로 내보낸 SVG다. 색은 Figma 값을 유지하고
 * (stroke 를 currentColor 로 바꾸지 않는다) 크기만 20px 로 맞춘다.
 */

const BASE = {
  width: 20,
  height: 20,
  viewBox: '0 0 20 20',
  fill: 'none',
  xmlns: 'http://www.w3.org/2000/svg',
  'aria-hidden': true,
} as const

const STROKE = {
  strokeWidth: 1.66667,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
} as const

/** 물품 경매 시작 (`ic_ev3_start`) */
export function StartIcon() {
  return (
    <svg {...BASE}>
      <path
        d="M6.6665 4.58398V15.4173L15.8332 10.0007L6.6665 4.58398Z"
        stroke="#4E5968"
        {...STROKE}
      />
    </svg>
  )
}

/** 낙찰 확정 (`ic_ev4_win`) */
export function WinIcon() {
  return (
    <svg {...BASE}>
      <path
        d="M10 17.5C14.1421 17.5 17.5 14.1421 17.5 10C17.5 5.85786 14.1421 2.5 10 2.5C5.85786 2.5 2.5 5.85786 2.5 10C2.5 14.1421 5.85786 17.5 10 17.5Z"
        stroke="#12A67A"
        {...STROKE}
      />
      <path
        d="M6.6665 10.166L9.1665 12.666L13.3332 7.66602"
        stroke="#12A67A"
        {...STROKE}
      />
    </svg>
  )
}

/** 마감 임박 (`ic_ev5_closing`) */
export function ClosingIcon() {
  return (
    <svg {...BASE}>
      <path
        d="M10 17.5C14.1421 17.5 17.5 14.1421 17.5 10C17.5 5.85786 14.1421 2.5 10 2.5C5.85786 2.5 2.5 5.85786 2.5 10C2.5 14.1421 5.85786 17.5 10 17.5Z"
        stroke="#E5484D"
        {...STROKE}
      />
      <path d="M10 6.25V10L12.5 11.6667" stroke="#E5484D" {...STROKE} />
    </svg>
  )
}

/** 입찰 (`ic_ev6_bid` / `ic_ev8_bid`) */
export function BidIcon() {
  return (
    <svg {...BASE}>
      <path d="M10 15.8327V4.16602" stroke="#3182F6" {...STROKE} />
      <path d="M5 9.16602L10 4.16602L15 9.16602" stroke="#3182F6" {...STROKE} />
    </svg>
  )
}

/** Soft Close 자동 연장 (`ic_ev7_softclose`) */
export function SoftCloseIcon() {
  return (
    <svg {...BASE}>
      <path
        d="M17.4998 9.99946C17.4859 11.7338 16.8713 13.4097 15.7608 14.7419C14.6502 16.0741 13.1122 16.9802 11.4086 17.306C9.70511 17.6317 7.94133 17.357 6.41757 16.5286C4.89382 15.7001 3.70429 14.3692 3.0515 12.7623C2.39871 11.1554 2.32302 9.37198 2.8373 7.71558C3.35158 6.05918 4.42405 4.63221 5.87212 3.67763C7.3202 2.72304 9.05436 2.29984 10.7794 2.48007C12.5044 2.6603 14.1136 3.43283 15.3331 4.66613"
        stroke="#E08700"
        {...STROKE}
      />
      <path d="M17.4998 2.91602V7.49935H12.9165" stroke="#E08700" {...STROKE} />
    </svg>
  )
}
