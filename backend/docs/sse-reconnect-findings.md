# SSE 재연결 구현 중 발견한 동작

## 1. SSE `id:` 필드가 원래 없었다

### 현상
기존 SSE 이벤트에 `id:` 필드가 전혀 없었다.

```
event: BID_PLACED
data: {"itemId":9,"itemName":"ㅇㅇㅇ","bidPrice":6001,"bidderNickname":"최서지"}
```

### 원인
`RoomSseManager.send()`가 `.id()`를 호출하지 않았다.

```java
// 기존
emitter.send(SseEmitter
    .event()
    .name(name)
    .data(data)   // .id() 없음 → SSE id 필드 자체가 없음
);
```

SSE 스펙에서 `id:` 필드는 명시적으로 `.id()`를 호출해야만 붙는다.
없으면 브라우저가 `Last-Event-ID`를 추적하지 못해 재연결 시 replay가 불가능하다.

### 해결
모든 SSE 이벤트에 순차 ID를 붙인다.

```
id: 1
event: ITEM_CLOSING_SOON
data: {...}

id: 2
event: BID_PLACED
data: {"itemId":9,...}

id: 3
event: SOFT_CLOSE_EXTENDED
data: {...}
```

---

## 2. 첫 BID_PLACED의 id가 1이 아닌 2였다

### 현상
첫 번째 입찰 후 BID_PLACED 이벤트의 id가 1이 아니라 2였다.

### 원인
입찰 하나가 처리될 때 여러 bufferable 이벤트가 함께 발행된다.
BID_PLACED보다 먼저 처리된 이벤트(예: `ITEM_CLOSING_SOON`)가 id=1을 가져갔다.

```
id=1 : ITEM_CLOSING_SOON   (입찰 처리 중 soft close 진입 감지)
id=2 : BID_PLACED
id=3 : SOFT_CLOSE_EXTENDED
```

### 판단
**정상 동작이다.** 순차 ID는 방 전체의 이벤트 순서를 추적하는 것이지,
특정 이벤트 타입만 세는 게 아니다.

---

## 3. PARTICIPANT_COUNT_UPDATED도 이전 이벤트의 id를 가리켰다

### 현상
BID_PLACED(id=2) 직후에 온 PARTICIPANT_COUNT_UPDATED의
`event.lastEventId`가 2로 찍혔다.

### 원인
SSE 스펙의 **sticky id** 동작이다.
현재 이벤트에 `id:` 필드가 없으면 브라우저는 마지막으로 본 id를 그대로 유지한다.

```
id: 2
event: BID_PLACED        ← 브라우저: Last-Event-ID = 2 저장

event: PARTICIPANT_COUNT_UPDATED   ← id 필드 없음
                                   ← 브라우저: Last-Event-ID 여전히 2
```

### 판단
처음에는 정상이라고 판단했으나, 이후 **모든 이벤트에 id를 붙이는 방향**으로 변경했다.

---

## 4. 모든 이벤트에 id를 붙이기로 결정

### 기존 설계
bufferable 이벤트에만 id를 붙이고, PARTICIPANT_COUNT·ROOM_UPDATED는 제외.

### 변경 이유
- `Last-Event-ID`가 "진짜 마지막으로 받은 이벤트"를 정확히 가리켜야 한다.
- PARTICIPANT_COUNT가 id 없이 오면 클라이언트의 `lastEventId`가 이전 bufferable 이벤트에 머문다.
  재연결 시 PARTICIPANT_COUNT 이후에 온 bufferable 이벤트를 유실할 수 있다.
- replay 로직(`dropWhile(id <= lastEventId)`)은 버퍼에 없는 id도 자연스럽게 건너뛰므로
  non-bufferable 이벤트에 id를 붙여도 replay에 문제없다.

### 변경 후
모든 이벤트가 순차 id를 받는다. `NON_BUFFERABLE_EVENTS` 구분 제거.

```
id: 1
event: ITEM_CLOSING_SOON
data: {...}

id: 2
event: BID_PLACED
data: {...}

id: 3
event: PARTICIPANT_COUNT_UPDATED
data: {...}

id: 4
event: SOFT_CLOSE_EXTENDED
data: {...}
```

재연결 시 `Last-Event-ID: 3`을 보내면 서버는 버퍼에서 id > 3인 것만 replay한다.
id=3(PARTICIPANT_COUNT)은 버퍼에 없지만 `dropWhile(id <= 3)`이 자연스럽게 처리한다.
