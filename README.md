<div align="center">

# UpBid

### 입찰은 빠르게, 낙찰은 정확하게

링크·QR 하나로 참여하는 SNS 연계 실시간 경매 서비스

![UpBid](https://github.com/user-attachments/assets/942847be-6d04-4c6e-9893-d91609eaf235)

</div>

---

<br>

## 목차

- [프로젝트 소개](#프로젝트-소개)
- [핵심 기능](#핵심-기능)
- [서비스 흐름](#서비스-흐름)
- [기술 스택](#기술-스택)
- [팀원 소개](#팀원-소개)
- [팀 문화](#팀-문화)
- [프로젝트 일정](#프로젝트-일정)
- [더 알아보기](#더-알아보기)

<br>

## 프로젝트 소개

인스타그램 라이브, 카카오톡 오픈채팅, X(트위터), 디스코드 — 이미 많은 사람들이 이런 SNS
안에서 중고 의류, 포토카드, 굿즈, 컬렉터블을 경매로 거래하고 있습니다. 문제는 SNS에
경매할 공간이 없는 게 아니라, 그 경매가 **댓글·채팅·DM·캡처로만 운영**된다는 점입니다.
누가 얼마에 입찰했는지 한눈에 보기 어렵고, 마감 시점을 둘러싼 다툼이 생기고, 낙찰 이후
연락이 끊기면 거래가 그대로 무산됩니다.

**UpBid**는 판매자가 이미 쓰고 있는 SNS 채널을 대체하지 않습니다. 대신 그 위에 링크와
QR로 들어오는 **실시간 경매방**을 얹어서, 입찰과 낙찰을 서버가 시간 기준으로 투명하게
관리하도록 돕습니다.

<br>

## 핵심 기능

| 기능 | 설명 |
|---|---|
| 빠른 참여 | 링크·QR로 바로 입장, 둘러보는 동안은 로그인 불필요. 입찰 순간에만 로그인 요청 |
| 실시간 경매 | 경매방 하나에 최대 3개 물품 동시 진행, 물품별 현재가·순위·마감까지 남은 시간 독립 표시 |
| 정확한 낙찰 | 서버 시간 기준 마감, 마감 직전 입찰 시 자동 연장(Soft Close), 조건 미달 입찰 자동 거절 |
| 투명한 기록 | 모든 입찰 이력·낙찰 결과 보존, 낙찰 실패 시 차순위 입찰자에게 자동으로 기회 이전 |

<br>

## 서비스 흐름

**판매자**

```
프로필/상점 준비 → 상품 등록 → 경매방 생성·물품 편성 → 링크·QR 공유
   → 경매 진행(실시간 모니터링) → 마감·낙찰 확정 → 낙찰자와 거래 진행
```

**참여자(입찰자)**

```
링크·QR 접속(비로그인 미리보기) → 경매방 입장 → 입찰 시점에 로그인
   → 실시간 입찰 → 현재가·순위 실시간 확인 → 마감 후 결과 확인
   → (낙찰 시) 판매자와 거래 진행 / (낙찰 실패 시) 차순위 대기
```

<br>

## 기술 스택

| 영역 | 스택 |
|---|---|
| Backend | Java 21, Spring Boot 4.x, Spring Data JPA |
| Database | MySQL 8.4 (RDS) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS |
| Infra | AWS(EC2, RDS, S3), Docker Compose, GitHub Actions |
| Test | JUnit 5 |

<br>

## 팀원 소개

| 이름 | GitHub | 담당 |
|---|---|---|
| 기승민 | [@KiSeungMin](https://github.com/KiSeungMin) | |
| 최서지 | [@choiseoji](https://github.com/choiseoji) | |
| 최한기 | [@choicold](https://github.com/choicold) | |
| 정우재 | [@Woojae-Jeong](https://github.com/Woojae-Jeong) | |
| 김원기 | [@cylin0201](https://github.com/cylin0201) | |

<br>

## 팀 문화

- **소통 리듬** — 데일리 스크럼(오전 10시·오후 6시), 정기 회의(월·화·목 오후 2시),
  매주 금요일 KPT 회고
- **데일리 회고** — 오후 데일리 스크럼을 마무리하며 그날 느낀 점을 각자 공유합니다.
  최근에는 다른 조의 방식을 참고해 "1일 1칭찬"도 함께 하고 있습니다.
- **진행 상황 공유** — 그날 완료한 일과 못한 일을 체크리스트로 정리하고, 다음에 할 일을
  공유합니다.
- **배포 주기** — 매일 오전 10시 데일리 스크럼 전에 `dev → main`을 머지하고 배포합니다.
- **코드 리뷰** — PR에는 관련 이슈·작업 개요·고민한 지점을 남기고, 최소 2명의 리뷰를
  받은 뒤 머지합니다.

### 브랜치·머지 전략

```
main
└── dev
    ├── be/feat/{issue}-{feature}
    └── fe/feat/{issue}-{feature}
```

- `main ← dev ← {영역}/{타입}/{이슈번호}-{기능}` 흐름을 따릅니다.
- 머지 방식: `{영역}/{타입}/{이슈}-{기능} → dev`는 **스쿼시 머지**, `dev → main`은
  일반 머지로 진행합니다.
- 커밋은 `[BE] <type>: <summary>` / `[FE] <type>: <summary>` 형식을 따릅니다.

<br>

## 프로젝트 일정

| 주차 | 기간 | 목표 |
|---|---|---|
| 1주차 | 07.27 ~ 08.02 | 최소 E2E 흐름 연결 |
| 2주차 | 08.03 ~ 08.09 | MVP 완성 |
| 3주차 | 08.10 ~ 08.16 | 통합 안정화·부하 검증·발표 자료 정리 |

<br>

## 더 알아보기

| 문서 | 링크 |
|---|---|
| 상세 기획안 (Notion) | https://amethyst-naranja-aac.notion.site/UpBid-3a64960319768083b497e9ef3596939c |
| GitHub Wiki | https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki |
| API 명세 (Swagger UI) | https://api.upbid.store/swagger-ui/index.html |
| ERD | https://www.erdcloud.com/d/4FRa83M5MbkZsMeYY |
| 디자인 (Figma) | https://www.figma.com/design/DVDS1Ie2YqLU25HW7HPXZB/Hot6ix |
