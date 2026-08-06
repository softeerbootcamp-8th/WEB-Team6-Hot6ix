# AuctionCloseAnimation

고정된 고양이 원화와 분리된 팔·망치 레이어로 렌더링한 완성 APNG를 표시하는 React 컴포넌트입니다. 팔만 움직여 보이지 않도록 발을 고정한 채 어깨·몸통·머리가 함께 준비하고 타격을 따라갑니다. 30fps의 세 번 타격과 마지막에 화면 정중앙에 크게 찍히는 빨간 `낙찰` 도장은 이미지 파일 내부에 있으며 React와 CSS에는 관절 회전이나 프레임 애니메이션 로직이 없습니다.

배경 없는 반복 재생 미리보기는 [`../assets/auction-cat-cel-v2/auction-cat-preview.png`](../assets/auction-cat-cel-v2/auction-cat-preview.png)에서 확인할 수 있습니다. 실제 `ended`와 `sold` APNG는 세 번 타격한 뒤 정지합니다.

```tsx
import { AuctionCloseAnimation } from "./components/AuctionCloseAnimation";

export function AuctionResult({ auctionId, winnerId }: Props) {
  return (
    <AuctionCloseAnimation
      outcome={winnerId ? "sold" : "closed"}
      replayKey={auctionId}
      size={320}
    />
  );
}
```

## Props

- `outcome`: `"closed" | "sold"` (기본값 `"closed"`)
- `size`: 숫자는 px, 문자열은 CSS 크기로 적용
- `replayKey`: 값이 바뀌면 APNG 이미지가 다시 마운트되어 처음부터 재생
- `alt`: 접근성 대체 텍스트

완성 파일과 원본 레이어·프레임은 `../assets/auction-cat-cel-v2/`에서 불러옵니다. 브라우저의 모션 축소 설정이 켜져 있으면 APNG 대신 정지된 최종 결과가 표시됩니다.
