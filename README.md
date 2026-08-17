<div align="center">

# UpBid

### 입찰은 빠르게, 낙찰은 정확하게

링크와 QR 하나로 참여하는 SNS 연계 실시간 경매 서비스

![UpBid](https://github.com/user-attachments/assets/41de22a8-c9ff-4021-93dd-c0a7ce8162f3)

<table>
  <tr>
    <td align="center" width="240">
      <b>🛎️&nbsp; 서비스 열기</b><br/>
      <a href="https://www.upbid.store">upbid.store</a>
    </td>
    <td align="center" width="240">
      <b>📖&nbsp; 기술 문서</b><br/>
      <a href="https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki">GitHub Wiki</a>
    </td>
    <td align="center" width="240">
      <b>🧾&nbsp; API 문서</b><br/>
      <a href="https://api.upbid.store/swagger-ui/index.html">Swagger UI</a>
    </td>
  </tr>
</table>

</div>

---

<br>

## 목차

- [프로젝트 소개](#프로젝트-소개)
- [핵심 기능](#핵심-기능)
- [서비스 화면](#서비스-화면)
- [기술 스택](#기술-스택)
- [시스템 구조](#시스템-구조)
- [문서](#문서)
- [팀원 소개](#팀원-소개)
- [실행 방법](#실행-방법)

<br>

## 프로젝트 소개

> **SNS에서 이미 벌어지고 있는 경매를, 댓글 대신 서버가 판정하게 합니다.**

인스타그램 라이브와 오픈채팅에서 굿즈와 중고 의류가 경매로 거래됩니다. 경매할 공간이 없어서가 아니라, **그 경매를 댓글과 캡처로 판정하는 것**이 문제입니다.

| | 💬 댓글로 하는 경매 | 🛎️ UpBid |
| --- | --- | --- |
| **최고가** | 댓글을 거슬러 올라가 확인 | 화면 위에 고정 |
| **입찰 순서** | 정렬과 지연으로 뒤집힘 | 서버가 받은 순서로 확정 |
| **마감** | 판매자가 눈으로 끊음 | 서버 시간 기준 자동 |
| **낙찰자** | 최고가와 순서를 수동 대조 | 최고 입찰자 자동 확정 |
| **거래 불발** | 차순위를 다시 찾아 연락 | 기록된 순위로 자동 승계 |
| **기록** | 끝나면 흩어짐 | 입찰 이력과 결과가 남음 |

```
SNS에서 발견  →  UpBid에서 입찰  →  서버가 낙찰 확정  →  외부에서 거래
```

<br>

## 핵심 기능

### ⚡ 링크 하나로 참여

![링크로 참여](https://github.com/user-attachments/assets/70ed7fc7-3116-4aec-bc03-ef54adb36c8d)

- **링크와 QR로 바로 들어옵니다.** 공개 주소는 숫자 PK가 아니라 `share_code`를 씁니다
- **로그인 전에도 경매방과 물품을 볼 수 있습니다.** 입찰할 때만 로그인을 요청하고, 끝나면 보던 경매방으로 돌아옵니다

### 🔴 모두가 같은 화면을 봅니다

![동시 입찰](https://github.com/user-attachments/assets/462cf48e-efa6-4a1f-ab09-27fa293c2ddc)

- **입찰이 몰려도 현재가와 리더보드가 접속자 전원에게 같은 순서로 반영됩니다.** 위 화면은 14초 동안 59건이 들어오는 상황입니다
- 한 경매방에서 물품 최대 3개가 독립적인 현재가와 카운트다운으로 동시에 돕니다
- SSE로 밀어 주고, 서버가 여러 대여도 Redis Pub/Sub으로 같은 이벤트가 나갑니다

### 🎯 마감은 서버가 정합니다

![Soft Close](https://github.com/user-attachments/assets/9aca3784-d250-43e4-9deb-dcd702dd2f86)

- **사용자 기기가 아니라 서버 시간 기준**으로 카운트다운하고 마감합니다
- **Soft Close.** 마감 직전에 입찰이 들어오면 그 물품만 자동으로 연장합니다. 위 화면에서 60초가 늘어납니다
- 현재가 이하, 입찰 단위 불일치, 마감 후, 판매자 본인 입찰은 서버가 거절합니다

### 📋 불발되면 차순위로 넘어갑니다

![거래 성사와 실패](https://github.com/user-attachments/assets/c67db920-ff78-4aca-9538-4270e3590a02)

- **낙찰 후보를 순위대로 남깁니다.** 1순위 거래가 깨지면 2순위에게 자동으로 넘어갑니다
- 물품별 입찰 이력이 그대로 남아서, 왜 그 사람이 낙찰됐는지 확인할 수 있습니다

<br>

## 서비스 화면

모바일에서도 같은 경매방에 그대로 들어옵니다.

<table>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/01d50d20-5ec7-4bb0-b90a-8260b977721e" width="230" alt="경매방 입장" /><br/><b>경매방 입장</b><br/><sub>동의 한 번으로 들어옵니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/8325487d-2520-4ffe-9c01-3d65c3a0c638" width="230" alt="경매방" /><br/><b>경매방</b><br/><sub>물품과 실시간 이벤트를 함께 봅니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/98066282-f4e8-448e-85c4-9ffebab438e2" width="230" alt="빠른 입찰" /><br/><b>빠른 입찰</b><br/><sub>바텀시트에서 진행 중인 물품에 바로 입찰합니다</sub></td>
  </tr>
</table>

<details>
<summary><b>화면 더 보기</b> (입찰 흐름, 판매자 조작, 모바일 12장)</summary>

<br/>

### 입찰

![입찰하기](https://github.com/user-attachments/assets/934160d1-6e10-4b2d-857b-616bb272cc40)

**입찰 단위 버튼으로 금액을 올리고, 확정 전에 한 번 더 확인합니다.** 승인되면 이벤트 피드와 리더보드, 현재가가 접속 중인 모든 참여자에게 즉시 반영됩니다.

### 판매자

<table>
  <tr>
    <td width="50%"><img src="https://github.com/user-attachments/assets/34ca1862-616d-4895-8893-51b4272cab0b" alt="마감 앞당기기" /></td>
    <td width="50%"><img src="https://github.com/user-attachments/assets/3d5defaa-8392-4498-ac24-32224eb29868" alt="경매방 종료" /></td>
  </tr>
  <tr>
    <td align="center"><b>마감 앞당기기</b><br/><sub>남길 시간을 지정해 앞당깁니다. 그 사이 입찰이 들어오면 다시 연장됩니다</sub></td>
    <td align="center"><b>경매방 종료</b><br/><sub>낙찰과 유찰 건수, 총 낙찰액을 정산해 결과 화면으로 남깁니다</sub></td>
  </tr>
</table>

### 모바일

<table>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/9f1ac88e-4b98-429e-80c1-b4ec9b083735" width="230" alt="내 경매방" /><br/><b>내 경매방</b><br/><sub>만든 방과 참여한 방을 함께 봅니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/f9d94d90-905b-4b3c-8fe9-0b031fdc5f2d" width="230" alt="입찰 규칙" /><br/><b>입찰 규칙</b><br/><sub>입찰 단위와 마감 연장 조건을 정합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/0cc4836d-50f6-4533-a16b-38e41c3f17b9" width="230" alt="판매 물품 선택" /><br/><b>판매 물품 선택</b><br/><sub>등록해 둔 상품에서 골라 담습니다</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/99ddd58a-e0c5-40a9-961e-fbbf1407e861" width="230" alt="공유 QR" /><br/><b>공유 QR</b><br/><sub>링크와 QR을 그 자리에서 만듭니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/6dc00ba0-b424-454a-aaf5-8ee33dc23fe3" width="230" alt="물품 상세" /><br/><b>물품 상세</b><br/><sub>설명과 현재가, 남은 시간을 봅니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/28092082-a80c-4df7-923e-eadb1db6906a" width="230" alt="리더보드" /><br/><b>리더보드</b><br/><sub>물품별 순위와 내 위치를 봅니다</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/f522ba6a-daac-451c-8ea9-8bd927f071a6" width="230" alt="입찰 확정" /><br/><b>입찰 확정</b><br/><sub>금액을 확인하고 확정합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/ee6a0056-6764-4fc9-81a1-2715127700f0" width="230" alt="입찰 등록" /><br/><b>입찰 등록</b><br/><sub>서버가 승인한 뒤에 확정으로 표시합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/fba1cec2-3bc0-4963-ab8e-c6b0a2484646" width="230" alt="마감 앞당기기" /><br/><b>마감 앞당기기</b><br/><sub>판매자가 남길 시간을 정합니다</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/0e08058d-a615-4591-b35b-0fe29342be0d" width="230" alt="종료 결과" /><br/><b>종료 결과</b><br/><sub>낙찰과 유찰, 총 낙찰액을 한 화면에 정리합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/09df720d-ffd2-4d72-b61c-b3a00e6a878f" width="230" alt="낙찰 후보" /><br/><b>낙찰 후보</b><br/><sub>순위대로 남겨 두고 차순위로 넘깁니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/74c56aa4-e01f-433e-918b-51bf8de09f0d" width="230" alt="거래 완료" /><br/><b>거래 완료</b><br/><sub>성사된 거래를 기록으로 남깁니다</sub></td>
  </tr>
</table>

</details>

<br>

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| **Backend** | ![Java 21](https://img.shields.io/badge/Java%2021-007396?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot 4.x](https://img.shields.io/badge/Spring%20Boot%204.x-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=flat-square&logo=spring&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL-0769AD?style=flat-square) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white) |
| **Database** | ![MySQL 8.4](https://img.shields.io/badge/MySQL%208.4-4479A1?style=flat-square&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-FF4438?style=flat-square&logo=redis&logoColor=white) ![ShedLock](https://img.shields.io/badge/ShedLock-555555?style=flat-square) |
| **실시간 통신** | ![SSE](https://img.shields.io/badge/SSE-5B21B6?style=flat-square) ![Redis Pub%2FSub](https://img.shields.io/badge/Redis%20Pub%2FSub-FF4438?style=flat-square&logo=redis&logoColor=white) ![Redis Stream](https://img.shields.io/badge/Redis%20Stream-FF4438?style=flat-square&logo=redis&logoColor=white) |
| **Frontend** | ![React 19](https://img.shields.io/badge/React%2019-61DAFB?style=flat-square&logo=react&logoColor=black) ![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white) ![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white) ![TanStack Router%2FQuery](https://img.shields.io/badge/TanStack%20Router%2FQuery-FF4154?style=flat-square&logo=reactquery&logoColor=white) ![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white) ![shadcn%2Fui](https://img.shields.io/badge/shadcn%2Fui-000000?style=flat-square&logo=shadcnui&logoColor=white) ![Orval](https://img.shields.io/badge/Orval-1E1E1E?style=flat-square) |
| **Infra** | ![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white) ![EC2](https://img.shields.io/badge/EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white) ![RDS](https://img.shields.io/badge/RDS-527FFF?style=flat-square&logo=amazonrds&logoColor=white) ![S3](https://img.shields.io/badge/S3-569A31?style=flat-square&logo=amazons3&logoColor=white) ![CloudFront](https://img.shields.io/badge/CloudFront-8C4FFF?style=flat-square&logo=amazoncloudfront&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white) ![Nginx](https://img.shields.io/badge/Nginx-009639?style=flat-square&logo=nginx&logoColor=white) ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white) |
| **관측** | ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white) ![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white) ![Loki](https://img.shields.io/badge/Loki-F46800?style=flat-square&logo=grafana&logoColor=white) ![Slack](https://img.shields.io/badge/Slack-4A154B?style=flat-square&logo=slack&logoColor=white) |
| **Test · 측정** | ![JUnit 5](https://img.shields.io/badge/JUnit%205-25A162?style=flat-square&logo=junit5&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-291A54?style=flat-square&logo=testcontainers&logoColor=white) ![k6](https://img.shields.io/badge/k6-7D64FF?style=flat-square&logo=k6&logoColor=white) |

주요 선택의 근거는 [고른 것들](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-기술-스택)과 [기술 선택의 변화](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/회고-기술-선택의-변화)에 있습니다. 처음 정한 것과 최종이 달라진 항목을 왜 바꿨는지까지 정리했습니다.

<br>

## 시스템 구조

> 🚧 Redis 도입과 다중 인스턴스 구성을 반영한 아키텍처 다이어그램은 교체 예정입니다.

### 데이터 모델

![ERD](https://github.com/user-attachments/assets/da9e92ab-a424-4625-ba80-852f8491a217)

원본은 [ERDCloud](https://www.erdcloud.com/d/4FRa83M5MbkZsMeYY)에 있습니다.

### 디렉터리 구조

```
.
├── backend/                        Spring Boot 4 · Java 21
│   └── src/main/
│       ├── java/com/hot6ix/upbid/
│       │   ├── domain/             auction, bid, deal, auth, user, product, sse, upload
│       │   │   └── auction/        controller, api, service, store, repository, entity, dto, scheduler
│       │   └── global/             config, redis, session, event, exception, metrics, logging, alert
│       └── resources/
│           ├── db/migration/       Flyway 마이그레이션
│           └── lua/                입찰 판정과 마감을 원자적으로 처리하는 Lua 스크립트
└── frontend/                       React 19 · Vite 6 · TypeScript
    └── src/
        ├── routes/                 TanStack Router 파일 기반 라우트
        ├── features/               auth, live, rooms, seller, trades, legal
        ├── api/generated/          Orval 이 OpenAPI 로 생성 (직접 수정하지 않습니다)
        ├── components/             공용 UI 와 레이아웃
        └── lib/                    session, toast, format, route-guards
```

**도메인마다 `controller → service → repository`를 그대로 반복합니다.** 다만 입찰과 마감처럼 동시성이 걸리는 도메인은 `store`를 하나 더 두어, Redis Lua 로 판정하는 부분을 Service 에서 분리했습니다.

<br>

## 문서

**기술 문서는 [GitHub Wiki](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki)에 있습니다.** "무엇을 만들었나"보다 "왜 이렇게 만들었나"를 남긴 아카이브입니다.

| 분류 | 내용 |
| --- | --- |
| 📌 [프로젝트](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-서비스-소개) | 서비스 소개, 기획 배경, 주요 기능, 기술 스택, 팀원과 역할 |
| 🏗️ [아키텍처](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Architecture-전체-시스템-아키텍처) | 전체 구조, 백엔드와 프론트엔드, 도메인 모델, 데이터 흐름, API 계약 |
| ⚡ [실시간 경매](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-경매-상태와-전이-규칙) | 상태 전이, 입찰 정책과 동시성, Soft Close, 마감 스케줄링, 낙찰과 차순위 |
| 📡 [실시간 통신](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/실시간-통신-SSE-도입-이유) | SSE 도입 이유, 이벤트 계약, 재연결, 멀티 인스턴스 전파 |
| 🧠 [Redis](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Redis-도입-배경) | 도입 배경, Key 설계, 지연 큐, 분산 락, MySQL 정합성 |
| 🔐 [인증과 권한](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Auth-인증-구조-개요) | 세션 vs JWT, 카카오 OAuth, 전화번호 인증, 게스트 접근 |
| 💾 [데이터베이스](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Database-MySQL-스키마) | 스키마, 도메인 모델, 인덱스 설계, Flyway, Soft Delete |
| 📊 [성능 측정](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Performance-부하-테스트-전략) | 측정 전략과 지표, Baseline, 병목 분석, 개선 후 재측정 |
| ☁️ [인프라](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Infra-AWS-구성) | AWS 구성, CI/CD, 환경 분리, 측정용 환경 |
| 📈 [모니터링](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Monitoring-Prometheus-지표) | Prometheus, Grafana, Loki, Slack 알림, 운영 대응 절차 |
| 🔧 [트러블슈팅](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/TS-운영-서버-OOM) | OOM, Full GC, 타임존, 데이터 소실 등 14건 |
| 🤝 [개발 문화](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Dev-팀-협업-규칙) | 협업 규칙, 브랜치 전략, 컨벤션, 테스트, AI 활용 |
| 🗒️ [회의록](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/회의록-목록) | 날짜별 스크럼과 주간 회의 |
| 🔄 [회고](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/회고-주간-회고) | 주간 회고, 기술 선택의 변화, 잘한 점과 아쉬운 점 |

그 밖의 자료입니다.

| 구분 | 링크 |
| --- | --- |
| 서비스 | <https://www.upbid.store> |
| API 명세 (Swagger UI) | <https://api.upbid.store/swagger-ui/index.html> |
| ERD | <https://www.erdcloud.com/d/4FRa83M5MbkZsMeYY> |
| 상세 기획안 (Notion) | <https://amethyst-naranja-aac.notion.site/UpBid-3a64960319768083b497e9ef3596939c> |

<br>

## 팀원 소개

<table>
  <tr>
    <td align="center" width="20%"><img src="https://github.com/user-attachments/assets/508bd507-dd85-49d5-baa6-f250ce1141c8" width="130" alt="기승민" /></td>
    <td align="center" width="20%"><img src="https://github.com/user-attachments/assets/6c4f3fea-b740-4e93-9f1a-cd9f4fbddb8c" width="130" alt="김원기" /></td>
    <td align="center" width="20%"><img src="https://github.com/user-attachments/assets/2895de91-b51d-4a7a-afcb-ff57f09a7f30" width="130" alt="정우재" /></td>
    <td align="center" width="20%"><img src="https://github.com/user-attachments/assets/c00b45ee-9954-4f84-8120-c44b0544dbbd" width="130" alt="최서지" /></td>
    <td align="center" width="20%"><img src="https://github.com/user-attachments/assets/b17f8a7f-a966-4d58-a193-ab9b36826ed2" width="130" alt="최한기" /></td>
  </tr>
  <tr>
    <td align="center"><b>기승민</b><br/><a href="https://github.com/KiSeungMin">@KiSeungMin</a></td>
    <td align="center"><b>김원기</b><br/><a href="https://github.com/cylin0201">@cylin0201</a></td>
    <td align="center"><b>정우재</b><br/><a href="https://github.com/Woojae-Jeong">@Woojae-Jeong</a></td>
    <td align="center"><b>최서지</b><br/><a href="https://github.com/choiseoji">@choiseoji</a></td>
    <td align="center"><b>최한기</b><br/><a href="https://github.com/choicold">@choicold</a></td>
  </tr>
  <tr>
    <td align="center"><sub><b>팀장</b><br/>경매방과 상품<br/>물품 생명주기</sub></td>
    <td align="center"><sub>도메인 이벤트와 낙찰<br/>AWS 인프라</sub></td>
    <td align="center"><sub>회원과 세션<br/>SSE 실시간 통신</sub></td>
    <td align="center"><sub>회원과 인증<br/>SSE 실시간 통신</sub></td>
    <td align="center"><sub>입찰과 리더보드<br/>입찰 동시성</sub></td>
  </tr>
</table>

역할을 어떻게 나눴는지는 [누가 뭐를 맡았나](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-팀원-및-역할)에 있습니다.

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

macOS에서는 `backend/UpBid 개발환경 켜기.command`를 더블클릭하면 MySQL과 Grafana, 프론트, 백엔드를 한 번에 띄웁니다. 자세한 내용은 [로컬에서 띄우기](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Dev-로컬-실행-방법)를 참고하세요.
