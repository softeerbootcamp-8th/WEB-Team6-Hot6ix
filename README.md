<div align="center">

# UpBid

### 입찰은 빠르게, 낙찰은 정확하게

링크와 QR 하나로 참여하는 SNS 연계 실시간 경매 서비스

![UpBid](https://github.com/user-attachments/assets/41de22a8-c9ff-4021-93dd-c0a7ce8162f3)

[![서비스](https://img.shields.io/badge/서비스-upbid.store-3182F6?style=for-the-badge)](https://www.upbid.store)
[![Wiki](https://img.shields.io/badge/기술_문서-Wiki-4A90E2?style=for-the-badge)](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki)
[![Swagger](https://img.shields.io/badge/API-Swagger-6DB33F?style=for-the-badge)](https://api.upbid.store/swagger-ui/index.html)

**소프티어 부트캠프 8기 · Team 6. Hot6ix**

</div>

---

<br>

## 목차

- [프로젝트 소개](#프로젝트-소개)
- [서비스 화면](#서비스-화면)
- [핵심 기능](#핵심-기능)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [기술적 도전](#기술적-도전)
- [팀원 소개](#팀원-소개)
- [문서](#문서)
- [실행 방법](#실행-방법)

<br>

## 프로젝트 소개

인스타그램 라이브, 유튜브, 카카오톡 오픈채팅, X, 디스코드에서 이미 많은 사람들이 중고 의류와 포토카드, 굿즈, 컬렉터블을 경매로 거래하고 있습니다.

문제는 SNS에 경매할 공간이 없다는 게 아니라, **그 경매가 댓글과 채팅, DM, 캡처로만 운영된다**는 점입니다.

| 판매자가 겪는 문제 | 참여자가 겪는 문제 |
| --- | --- |
| 상품을 설명하면서 댓글과 입찰 금액을 동시에 확인해야 합니다 | 현재 최고가와 내 순위를 알기 어렵습니다 |
| 최고 입찰자와 댓글 순서를 직접 비교해야 합니다 | 댓글 정렬과 네트워크 차이로 결과를 신뢰하기 어렵습니다 |
| 마감 시점과 낙찰자를 수동으로 판정해야 합니다 | 왜 낙찰되거나 탈락했는지 근거를 볼 수 없습니다 |
| 낙찰 불발 시 차순위자를 다시 찾아야 합니다 | 종료 후 상품과 입찰, 낙찰 기록이 남지 않습니다 |

**UpBid는 판매자가 이미 쓰고 있는 SNS 채널을 대체하지 않습니다.** 그 위에 링크와 QR로 들어오는 실시간 경매방을 얹어서, 입찰과 마감, 낙찰을 서버가 시간 기준으로 판정하도록 돕습니다.

```
SNS에서 발견 → UpBid에서 입찰 → 서버가 낙찰 확정 → 외부에서 거래
```

<br>

## 서비스 화면

### 실시간 입찰

![실시간 입찰](https://github.com/user-attachments/assets/7a7a1637-def5-44b2-9255-01695e868fed)

입찰하기부터 확인, 확정, 화면 반영까지입니다. 입찰이 승인되면 이벤트 피드와 리더보드, 현재가가 접속 중인 모든 참여자에게 즉시 반영됩니다.

### 경매방 (구매자)

![경매방 구매자 시점](https://github.com/user-attachments/assets/0e91164e-fc91-4066-a370-cd9c17ff3c72)

왼쪽은 물품 목록, 가운데는 실시간 이벤트 피드, 오른쪽은 물품별 리더보드입니다. **경매방 하나에서 최대 3개 물품이 독립적으로 동시 진행**되고, 물품마다 현재가와 남은 시간이 따로 흐릅니다.

<table>
  <tr>
    <td width="50%"><img src="https://github.com/user-attachments/assets/d029a443-f08f-40f8-b76d-f32640b32bfb" alt="링크 참여" /></td>
    <td width="50%"><img src="https://github.com/user-attachments/assets/0a8effca-e40a-415d-a05e-90b22b5b7dd3" alt="입찰 확인" /></td>
  </tr>
  <tr>
    <td align="center"><b>링크로 참여</b><br/><sub>로그인 전에도 물품을 확인할 수 있고, 처음 입장할 때 한 번만 동의합니다</sub></td>
    <td align="center"><b>오입찰 방지</b><br/><sub>입찰 단위 버튼으로 금액을 올리고, 확정 전에 한 번 더 확인합니다</sub></td>
  </tr>
</table>

### 경매방 (판매자)

![경매방 판매자 시점](https://github.com/user-attachments/assets/ce0140f4-cf7f-4ac8-8b72-e6ef83cf5f5f)

판매자는 같은 화면에서 물품을 편성하고 시작합니다. 진행 중인 물품마다 마감을 앞당길 수 있고, 오른쪽 위에서 공유와 설정, 경매방 종료를 합니다.

<table>
  <tr>
    <td width="50%"><img src="https://github.com/user-attachments/assets/6d3a47d7-6a2a-4ef9-b16b-a9cd8b98f317" alt="마감 앞당기기" /></td>
    <td width="50%"><img src="https://github.com/user-attachments/assets/f1fcd421-74a8-4457-b7c6-d168f4d51109" alt="낙찰 완료" /></td>
  </tr>
  <tr>
    <td align="center"><b>마감 앞당기기</b><br/><sub>남길 시간을 지정해서 앞당깁니다. 그 사이 입찰이 들어오면 다시 연장됩니다</sub></td>
    <td align="center"><b>낙찰 확정</b><br/><sub>입찰부터 마감 앞당김, 낙찰까지 한 피드에 남습니다</sub></td>
  </tr>
</table>

### 종료와 결과

![경매방 결과](https://github.com/user-attachments/assets/90e83857-b992-41e9-bb82-17ede2f39f0f)

전체 물품과 낙찰, 유찰 건수, 총 낙찰액을 요약합니다. 물품별 낙찰자와 낙찰가, 그리고 거래 진행 상황을 함께 봅니다.

### 모바일

<table>
  <tr>
    <td width="50%" align="center"><img src="https://github.com/user-attachments/assets/96e16b29-3025-4636-94fc-566939cf2e00" width="320" alt="모바일 경매방" /></td>
    <td width="50%" align="center"><img src="https://github.com/user-attachments/assets/20f73d36-f5d0-4e79-b439-e9485e78c8f3" width="320" alt="모바일 입찰" /></td>
  </tr>
  <tr>
    <td align="center"><b>경매방</b><br/><sub>이벤트와 리더보드를 탭으로 전환합니다</sub></td>
    <td align="center"><b>입찰</b><br/><sub>진행 중인 물품에 바텀시트로 입찰합니다</sub></td>
  </tr>
</table>

<br>

## 핵심 기능

### ⚡ 빠른 참여

- 링크와 QR로 경매방 접속. 공개 주소는 숫자 PK가 아니라 `share_code`를 씁니다
- **비로그인 상태에서도 경매방과 물품을 볼 수 있습니다.** 입찰할 때만 로그인을 요청하고, 완료 후 보던 경매방으로 돌아옵니다
- 입찰 단위 버튼과 직접 입력을 함께 지원하고, 확정 전 확인 단계로 오입찰을 막습니다

### 🔴 실시간 경매

- 판매자가 물품별로 경매를 시작합니다. 한 경매방에서 최대 3개까지 동시 진행합니다
- 물품마다 독립적인 현재가와 리더보드, 카운트다운을 제공합니다
- 입찰 성공과 거절, 최고가 변경을 접속 중인 모든 참여자에게 즉시 반영합니다

### 🎯 정확한 낙찰

- **사용자 기기가 아니라 서버 시간 기준**으로 카운트다운하고 마감합니다
- 현재가 이하, 입찰 단위 불일치, 마감 후, 판매자 본인 입찰을 자동으로 거절합니다
- **Soft Close**. 종료 직전 입찰이 들어오면 해당 물품만 자동으로 연장합니다
- 마감 시 물품별 최고 입찰자를 낙찰자로 자동 확정합니다

### 📋 투명한 기록

- 물품별 전체 입찰 이력과 리더보드를 보존합니다
- 경매방 종료 후 모든 물품의 낙찰 결과를 봅니다
- **거래가 불발되면 기록된 순위에 따라 차순위 입찰자에게 기회가 넘어갑니다**

<br>

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| **Backend** | Java 21 · Spring Boot 4.x · Spring Data JPA · QueryDSL · Flyway |
| **Database** | MySQL 8.4 (RDS) |
| **Cache · Realtime** | Redis (Lua, Stream, ZSET, Pub/Sub) · Spring Session Data Redis · ShedLock |
| **실시간 통신** | SSE (Server-Sent Events) |
| **Frontend** | React 19 · TypeScript · Vite · TanStack Router/Query · Orval · Tailwind CSS · shadcn/ui |
| **Infra** | AWS (EC2, RDS, S3, CloudFront) · Docker · GitHub Actions |
| **관측** | Actuator · Prometheus · Grafana · Loki · Slack Webhook |
| **Test · 측정** | JUnit 5 · Testcontainers · k6 |

주요 선택의 근거는 [Wiki 1.5 기술 스택](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-기술-스택)과 [14.2 기술 선택의 변화](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/회고-기술-선택의-변화)에 있습니다. 처음 정한 것과 최종이 달라진 항목을 왜 바꿨는지까지 정리했습니다.

<br>

## 아키텍처

> 🚧 Redis 도입과 다중 인스턴스 구성을 반영한 다이어그램으로 교체 예정입니다.

<br>

## 기술적 도전

단순한 상품 CRUD가 아니라, **여러 사용자가 동시에 참여하는 상황에서 입찰 순서와 현재가, 마감 시각, 낙찰 결과를 일관되게 유지하는 것**이 이 프로젝트의 핵심이었습니다.

### 1. 같은 물품에 입찰이 몰릴 때의 정합성

- **처음**에는 MySQL 비관적 락으로 물품 행을 잠갔습니다. 부하 측정 129회 전체에서 경합 충돌과 마감 실패가 0건이라 정확성은 확보했습니다
- **문제는 처리량**이었습니다. 한 물품에 몰릴 때 한계가 명확했고, 이건 서버를 늘려도 물품 행 락이라 변하지 않습니다
- **그래서 판정을 Redis Lua 스크립트로 옮겼습니다.** 입찰 판정과 Soft Close 연장, 멱등 키 캐시, 영속화 Stream 기록을 스크립트 하나에서 원자적으로 처리합니다
- **MySQL 저장은 Redis Stream Consumer가 비동기로** 맡습니다. 판정은 즉시, 영속성은 뒤에서 따라오는 구조입니다

> [3.3 비관적 락](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-경매-입찰-동시성-비관적-락) · [3.4 Redis Lua 원자적 입찰](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-경매-Redis-Lua-원자적-입찰) · [3.5 Stream 영속화](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-경매-Redis-Stream-입찰-영속화)

### 2. 서버가 여러 대일 때의 실시간 전파

- **WebSocket 대신 SSE를 골랐습니다.** 입찰은 REST로 보내고 결과만 전파하면 되어서 양방향 채널이 필요 없었습니다
- 대신 **재연결 시 이벤트 유실을 직접 해결**해야 했습니다. 이벤트 ID 발급과 버퍼 저장, 발행을 Lua 하나로 묶어 순서를 지킵니다
- 한 서버 안에서는 구독 스레드가 emitter를 순서대로 보내다 보니 접속 수에 비례해 전달이 늦어졌습니다. **emitter별 큐와 가상 스레드 전송**으로 바꿨습니다
- 서버가 여러 대가 되면 A서버에서 발행한 이벤트가 B서버 구독자에게 가지 않습니다. **Redis Pub/Sub으로 인스턴스 간 릴레이**를 붙였습니다

> [4.1 SSE 도입 이유](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-통신-SSE-도입-이유) · [4.4 팬아웃 구조](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-통신-팬아웃-구조) · [4.5 멀티 인스턴스 전파](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-통신-멀티-인스턴스-전파)

### 3. 마감 시각을 지키는 것

- 마감 예약을 **JVM 메모리(`ConcurrentHashMap`)에 들고 있었습니다.** 재시작하면 예약이 통째로 사라져 물품이 영원히 안 닫히고, 서버가 2대면 같은 물품을 중복 마감합니다
- **예약을 Redis ZSET 지연 큐로 꺼냈습니다.** 집어 갈 때 지우지 않고 visibility timeout만큼 미뤄서, 처리 중 서버가 죽어도 예약이 사라지지 않습니다
- 그래도 Redis가 비는 경우가 남아서, **DB를 보고 예약을 다시 채우는 재동기화**를 안전망으로 뒀습니다. 이 주기가 곧 마감이 늦어질 수 있는 최악값이 됩니다
- 그 재동기화 조회가 `auction_items` 전체를 훑고 있어서 **`(status, end_at)` 인덱스**를 넣었습니다. 끝난 물품은 지우지 않아 계속 쌓이는데 진행 중 물품은 항상 몇 건뿐인 구조였습니다

> [3.8 마감 예약과 스케줄링](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-경매-마감-예약과-스케줄링) · [5.5 지연 큐](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Redis-마감-예약-지연-큐) · [8.6 인덱스 설계](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Database-인덱스-설계)

### 4. 재는 방법 자체를 맞추는 것

- 다섯 명이 각자 노트북에서 재면 숫자를 합칠 수 없어서, **부하 발생기 버전부터 집계 규칙까지 저장소에 커밋해 고정**했습니다. p95를 그래프 점들의 평균으로 내지 않는 것 같은 규칙도 여기 포함됩니다
- 배포 서버에 **129회를 쟀고, 그 과정에서 결론을 두 번 뒤집었습니다.** 한 번은 환경변수가 배포 앱에 반영되지 않은 상태로 이틀치 결론을 냈고, 한 번은 측정 스크립트가 멱등 키 헤더를 빠뜨려 6,463건이 전부 거절된 채로 쟀습니다
- 그래서 **같은 조건 3회 전에는 결론을 내지 않고, 설정을 넣었으면 앱이 그 값으로 도는지 지표로 확인한다**는 규칙을 추가했습니다

> [6.1 부하 테스트 전략](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Performance-부하-테스트-전략) · [6.2 측정 지표](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Performance-측정-지표) · [11장 Trouble Shooting](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/TS-측정-설정-미반영)

측정 조건과 전후 수치는 [Wiki 6장 Performance](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Performance-부하-테스트-전략)에 있습니다.

<br>

## 팀원 소개

| 이름 | GitHub | 담당 |
| --- | --- | --- |
| 기승민 | [@KiSeungMin](https://github.com/KiSeungMin) | 판매자 프로필과 상품, 경매방. 물품 생명주기(시작, Soft Close, 마감 앞당기기, 자동 종료, 재시작 복구) |
| 최한기 | [@choicold](https://github.com/choicold) | 입찰과 현재가, 리더보드. 입찰 동시성과 Redis Lua 원자적 판정 |
| 정우재 | [@Woojae-Jeong](https://github.com/Woojae-Jeong) | 회원과 세션, 카카오 OAuth. SSE 실시간 통신과 Redis |
| 최서지 | [@choiseoji](https://github.com/choiseoji) | 회원과 세션, 전화번호 인증. SSE 실시간 통신과 Redis |
| 김원기 | [@cylin0201](https://github.com/cylin0201) | 도메인 이벤트와 기록, 낙찰 후보와 거래. AWS 인프라 |

역할을 어떻게 나눴고 그레이 영역을 어떻게 정했는지는 [Wiki 1.4 팀원 및 역할](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-팀원-및-역할)에 있습니다.

<br>

## 문서

**기술 문서는 [GitHub Wiki](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki)에 있습니다.** "무엇을 만들었나"보다 "왜 이렇게 만들었나"를 남긴 아카이브입니다.

| 섹션 | 내용 |
| --- | --- |
| [1. Project](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-서비스-소개) | 서비스 소개, 기획 배경, 팀원과 역할, 기술 스택 |
| [2. Architecture](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Architecture-전체-시스템-아키텍처) | 시스템 구성, 백엔드와 프론트 구조, API 계약 |
| [3. 실시간 경매](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-경매-상태와-전이-규칙) | 상태 전이, 입찰 정책, 동시성, Soft Close, 마감 스케줄링 |
| [4. 실시간 통신](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-통신-SSE-도입-이유) | SSE 선택 근거, 이벤트 계약, 재연결, 팬아웃 |
| [5. Redis](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Redis-도입-배경) | 도입 배경, Key 설계, 지연 큐, 분산 락, 정합성 |
| [6. Performance](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Performance-부하-테스트-전략) | 측정 전략과 규약, 병목 분석, 개선 결과 |
| [7. Auth & Security](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Auth-인증-구조-개요) | 세션과 JWT, 카카오 OAuth, 전화번호 인증, 권한 |
| [8. Database](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Database-MySQL-스키마) | 스키마, 도메인 모델, 인덱스, Flyway |
| [9. Infrastructure](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Infra-AWS-구성) | AWS 구성, CI/CD, 환경 분리 |
| [10. Monitoring](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Monitoring-Prometheus-지표) | 지표, 대시보드, 로그 수집, 장애 알림 |
| [11. Trouble Shooting](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/TS-운영-서버-OOM) | OOM, Full GC, 타임존, 데이터 소실 등 14건 |
| [12. Development](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Dev-팀-협업-규칙) | 협업 규칙, 브랜치와 컨벤션, 로컬 실행, AI 활용 |
| [13. 회의록](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/회의록-목록) | 날짜별 스크럼과 회의 기록 |
| [14. 회고](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/회고-주간-회고) | 주간 회고, 기술 선택의 변화 |

그 밖의 자료입니다.

| 구분 | 링크 |
| --- | --- |
| 서비스 | <https://www.upbid.store> |
| API 명세 (Swagger UI) | <https://api.upbid.store/swagger-ui/index.html> |
| ERD | <https://www.erdcloud.com/d/4FRa83M5MbkZsMeYY> |
| 상세 기획안 (Notion) | <https://amethyst-naranja-aac.notion.site/UpBid-3a64960319768083b497e9ef3596939c> |

<br>

## 실행 방법

Docker Desktop과 Java 21, pnpm이 필요합니다.

```bash
# 1. 백엔드 (MySQL 컨테이너 + 애플리케이션)
cd backend
docker compose up -d
./gradlew bootRun

# 2. 프론트엔드
cd frontend
pnpm install
pnpm dev
```

`http://localhost:5173`으로 접속합니다. 백엔드는 8080, Swagger UI는 `http://localhost:8080/swagger-ui/index.html`입니다.

**Redis는 별도로 띄워야 합니다.** 기본값은 `localhost:6379`이고, 세션과 입찰 판정, 마감 예약이 여기에 의존합니다.

macOS에서는 `backend/UpBid 개발환경 켜기.command`를 더블클릭하면 MySQL과 Grafana, 프론트, 백엔드를 한 번에 띄웁니다. 자세한 내용은 [Wiki 12.5 로컬 실행 방법](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Dev-로컬-실행-방법)을 참고하세요.

> ⚠️ `docker compose down -v`는 로컬 DB 데이터까지 삭제합니다. 팀 규칙상 임의 실행을 금지합니다.
