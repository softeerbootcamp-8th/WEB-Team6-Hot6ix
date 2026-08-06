# BidAcceptedMotion

서버에서 사용자의 직접 입찰을 정상 접수한 뒤, 입찰 버튼 근처에 한 번 표시하는 투명 APNG 마이크로 애니메이션입니다.

```tsx
<div className="bid-confirmation" aria-live="polite">
  <BidAcceptedMotion replayKey={bidRequestId} size={88} />
  <span>{amount.toLocaleString()}원 입찰 완료</span>
</div>
```

- 권장 표시 크기: 72~96px
- 전체 길이: 0.855초
- 서버 성공 응답 이후에만 마운트
- 자동입찰 단계마다 반복하지 않음
- 상태 문구는 HTML과 `aria-live`로 별도 제공
- `replayKey`에는 입찰 요청 ID처럼 매번 달라지는 값을 전달
- 모션 축소 설정에서는 금색 체크가 표시된 정지 이미지 사용
