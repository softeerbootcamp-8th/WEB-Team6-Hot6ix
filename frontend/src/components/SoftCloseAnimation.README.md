# SoftCloseAnimation

입찰 마감 직전 새 입찰로 시간이 연장됐을 때 사용하는 투명 배경 고양이 애니메이션입니다. `+` 버튼 누르기와 시계 방향 바늘 이동만 1.45초 안에 짧게 보여주고 바로 정지합니다. 낙찰 도장과 비슷한 전체 화면 결과 마크는 사용하지 않으며, 실제 연장 시간은 이미지 아래의 프레임 없는 `+N분 연장` 텍스트로 표시합니다. 고정 원화 레이어로 렌더링한 30fps·33프레임 APNG이며 CSS·JavaScript 모션은 없습니다.

```tsx
import { SoftCloseAnimation } from "./components/SoftCloseAnimation";

<SoftCloseAnimation
  extensionMinutes={5}
  replayKey={`${auctionId}-${extensionCount}`}
  size={300}
/>;
```

- `extensionMinutes`: 화면에 표시할 실제 연장 시간
- `replayKey`: 연장이 다시 발생할 때마다 값이 바뀌어야 APNG가 처음부터 재생됨
- `showLabel={false}`: 프로젝트에서 별도 안내 문구를 표시할 경우 라벨 숨김
- 모션 축소 설정 사용자는 자동으로 마지막 정지 이미지를 봄

완성 APNG와 원본 레이어는 `../assets/soft-close-cat-v4/`에 있습니다.
