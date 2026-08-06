# AuctionStartAnimation

물품 경매가 실제로 시작되는 순간 화면 중앙에 약 1.5초간 표시하는 투명 배경 애니메이션입니다. 고양이가 파란색·금색 손종을 한 번 자연스럽게 울립니다. APNG에는 글자가 없으며, 빨간 `LIVE` 배지와 `경매 시작` 문구는 React DOM으로 표시합니다.

30fps·34프레임 APNG를 그대로 재생하므로 React/CSS에서 팔 관절을 움직이지 않습니다. 원화 레이어는 모든 프레임에서 고정되고 팔·벨 레이어와 몸 전체의 작은 무게 이동만 미리 렌더링되어 있습니다.

```tsx
import { AuctionStartAnimation } from "./components/AuctionStartAnimation";

{showAuctionStart && (
  <AuctionStartAnimation
    replayKey={`${auctionId}-${startedAt}`}
    size={320}
  />
)}
```

- `replayKey`: 새 물품 경매가 시작될 때마다 달라지는 값을 전달해야 APNG가 처음부터 재생됩니다.
- `size`: 숫자는 px, 문자열은 `18rem`, `min(80vw, 360px)` 같은 CSS 길이로 처리됩니다.
- 컴포넌트는 스스로 사라지지 않습니다. 상위 UI에서 약 1.6초 뒤 언마운트하거나, 다음 화면 상태로 전환하세요.
- `prefers-reduced-motion: reduce` 사용자는 자동으로 마지막 정지 이미지를 봅니다.
- `showLabel={false}`: 프로젝트에서 별도 시작 문구를 표시할 경우 기본 DOM 라벨을 숨깁니다.
- 문구는 HTML이므로 서비스 폰트, 색상, 다국어 문구로 자유롭게 변경할 수 있습니다.

완성 APNG와 원본 레이어는 `../assets/auction-start-cat-v1/`에 있습니다.
