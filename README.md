<div align="center">

# UpBid

### SNS 경매를 위한 실시간 입찰 서비스

링크나 QR로 간편하게 참여할 수 있는 SNS 연계 실시간 경매 서비스입니다.

![UpBid](https://github.com/user-attachments/assets/41de22a8-c9ff-4021-93dd-c0a7ce8162f3)

<table>
  <tr>
    <td align="center" width="240">
      <b>🛎️&nbsp; 서비스</b><br/>
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

- [UpBid가 해결하려는 문제](#upbid가-해결하려는-문제)
- [주요 기능](#주요-기능)
- [화면 구성](#화면-구성)
- [기술 스택](#기술-스택)
- [시스템 구성](#시스템-구성)
- [관련 문서](#관련-문서)
- [팀 구성](#팀-구성)

<br>

## UpBid가 해결하려는 문제

인스타그램이나 유튜브 라이브에서는 굿즈와 중고 물품을 판매하기 위한 경매가 자주 열립니다. 경매를 진행할 공간은 이미 있지만, 댓글과 DM만으로 입찰 순서와 낙찰자를 판단해야 한다는 문제가 있습니다.

UpBid는 판매자가 직접 확인하던 입찰 순서와 최고가, 마감 시점, 낙찰자를 서버에서 처리합니다.

| | 💬 댓글로 하는 경매 | 🛎️ UpBid |
| --- | --- | --- |
| **최고가 확인** | 이전 댓글을 거슬러 올라가며 확인 | 현재 최고가를 화면 상단에 표시 |
| **입찰 순서** | 댓글 정렬이나 네트워크 지연에 따라 달라질 수 있음 | 서버에 도착한 순서대로 확정 |
| **마감 시점** | 판매자가 상황을 보고 직접 결정 | 서버 시간을 기준으로 자동 마감 |
| **낙찰자 선정** | 최고 입찰 금액과 작성 순서를 직접 대조 | 서버에서 최고 입찰자를 자동 선정 |
| **차순위 승계** | 차순위 입찰자를 다시 찾아 연락 | 저장된 순위에 따라 차순위 후보로 승계 |
| **경매 기록** | 별도로 남기지 않으면 사라짐 | 입찰 내역과 최종 결과를 서비스에 보관 |

![UpBid 핵심 Flow](https://github.com/user-attachments/assets/3ebce61e-d68b-44c9-85aa-13a2544f7af3)

판매자가 경매방을 만든 뒤 SNS에 링크나 QR을 공유하면, 구매자는 해당 경매방에 들어와 입찰할 수 있습니다. 입찰 검증과 마감, 낙찰자 선정은 UpBid가 담당하며, 실제 거래는 서비스 밖에서 판매자와 구매자가 진행합니다.

<br>

## 주요 기능

### 링크와 QR을 이용한 경매 참여

![링크로 참여](https://github.com/user-attachments/assets/70ed7fc7-3116-4aec-bc03-ef54adb36c8d)

- 판매자는 경매방 링크와 QR을 만들어 SNS에 바로 공유할 수 있습니다.
- 로그인하지 않은 사용자도 경매방과 판매 물품을 먼저 확인할 수 있습니다. 입찰하려면 로그인이 필요합니다.

### 실시간 입찰 반영

![동시 입찰](https://github.com/user-attachments/assets/462cf48e-efa6-4a1f-ab09-27fa293c2ddc)

- 여러 입찰이 동시에 들어와도 현재가와 리더보드는 서버에서 확정한 순서대로 모든 접속자에게 전달됩니다. 위 화면은 14초 동안 59건의 입찰이 발생한 상황입니다.
- 하나의 경매방에는 최대 3개의 물품을 등록할 수 있으며, 물품마다 현재가와 마감 시간이 독립적으로 관리됩니다.
- 서버에서 발생한 변경 사항은 SSE로 전달합니다. 서버가 여러 대로 구성된 환경에서는 Redis Pub/Sub을 통해 각 서버에 같은 이벤트를 전파합니다.

### 서버 시간을 기준으로 한 마감

![Soft Close](https://github.com/user-attachments/assets/9aca3784-d250-43e4-9deb-dcd702dd2f86)

- 경매 종료 시점은 사용자 기기의 시간이 아닌 서버 시간을 기준으로 계산합니다.
- 마감 직전에 입찰이 들어오면 해당 물품의 종료 시간만 자동으로 연장하는 Soft Close 방식을 적용했습니다. 위 화면에서는 입찰 후 남은 시간이 60초로 연장됩니다.
- 현재가 이하의 입찰, 입찰 단위에 맞지 않는 금액, 마감 이후의 입찰, 판매자 본인의 입찰은 서버에서 거절합니다.

### 차순위 낙찰 후보 승계

![거래 성사와 실패](https://github.com/user-attachments/assets/c67db920-ff78-4aca-9538-4270e3590a02)

- 경매가 끝나면 물품별 낙찰 후보를 입찰 순위대로 저장합니다. 1순위 후보와의 거래가 성사되지 않으면 다음 순위 후보에게 거래 기회가 넘어갑니다.
- 물품별 입찰 내역이 함께 보관되기 때문에 낙찰 후보가 결정된 과정을 확인할 수 있습니다.

<br>

## 화면 구성

PC와 모바일에서 같은 경매방에 접속할 수 있으며, 화면 크기에 맞춰 동일한 기능을 제공합니다.

<table>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/01d50d20-5ec7-4bb0-b90a-8260b977721e" width="230" alt="경매방 입장" /><br/><b>경매방 입장</b><br/><sub>입장 동의를 마치면 경매방에 참여합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/8325487d-2520-4ffe-9c01-3d65c3a0c638" width="230" alt="경매방" /><br/><b>경매방</b><br/><sub>판매 물품과 실시간 이벤트를 함께 확인합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/98066282-f4e8-448e-85c4-9ffebab438e2" width="230" alt="빠른 입찰" /><br/><b>빠른 입찰</b><br/><sub>진행 중인 물품을 바텀시트에서 선택해 입찰합니다</sub></td>
  </tr>
</table>

<details>
<summary><b>화면 더 보기</b> (입찰 흐름, 판매자 조작, 모바일 12장)</summary>

<br/>

### 입찰 과정

![입찰하기](https://github.com/user-attachments/assets/934160d1-6e10-4b2d-857b-616bb272cc40)

입찰 단위 버튼으로 금액을 조정한 뒤, 최종 확인 화면에서 입찰을 확정합니다. 서버가 입찰을 승인하면 이벤트 피드와 리더보드, 현재가가 갱신되고 현재 접속 중인 모든 참여자에게 같은 내용이 전달됩니다.

### 판매자 기능

<table>
  <tr>
    <td width="50%"><img src="https://github.com/user-attachments/assets/34ca1862-616d-4895-8893-51b4272cab0b" alt="마감 앞당기기" /></td>
    <td width="50%"><img src="https://github.com/user-attachments/assets/3d5defaa-8392-4498-ac24-32224eb29868" alt="경매방 종료" /></td>
  </tr>
  <tr>
    <td align="center"><b>마감 앞당기기</b><br/><sub>남길 시간을 지정해 마감을 앞당깁니다. 직전에 입찰이 들어오면 다시 연장됩니다</sub></td>
    <td align="center"><b>경매방 종료</b><br/><sub>낙찰 및 유찰 건수와 총 낙찰 금액을 집계해 결과 화면에 표시합니다</sub></td>
  </tr>
</table>

### 모바일 화면

<table>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/9f1ac88e-4b98-429e-80c1-b4ec9b083735" width="230" alt="내 경매방" /><br/><b>내 경매방</b><br/><sub>만든 경매방과 참여한 경매방을 한곳에서 확인합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/f9d94d90-905b-4b3c-8fe9-0b031fdc5f2d" width="230" alt="입찰 규칙" /><br/><b>입찰 규칙</b><br/><sub>입찰 단위와 마감 연장 조건을 설정합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/0cc4836d-50f6-4533-a16b-38e41c3f17b9" width="230" alt="판매 물품 선택" /><br/><b>판매 물품 선택</b><br/><sub>등록한 상품 중 경매에 올릴 물품을 선택합니다</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/99ddd58a-e0c5-40a9-961e-fbbf1407e861" width="230" alt="공유 QR" /><br/><b>공유 QR</b><br/><sub>경매방 링크와 QR을 생성해 공유합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/1174c5ed-7648-4e06-a31b-d077432989e7" width="230" alt="물품 상세" /><br/><b>물품 상세</b><br/><sub>상품 설명과 현재가, 남은 시간을 확인합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/28092082-a80c-4df7-923e-eadb1db6906a" width="230" alt="리더보드" /><br/><b>리더보드</b><br/><sub>물품별 입찰 순위와 사용자의 현재 순위를 확인합니다</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/f522ba6a-daac-451c-8ea9-8bd927f071a6" width="230" alt="입찰 확정" /><br/><b>입찰 확정</b><br/><sub>제출하기 전에 입찰 금액을 다시 확인합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/ee6a0056-6764-4fc9-81a1-2715127700f0" width="230" alt="입찰 등록" /><br/><b>입찰 등록</b><br/><sub>서버가 승인한 이후에만 등록 완료로 표시합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/fba1cec2-3bc0-4963-ab8e-c6b0a2484646" width="230" alt="마감 앞당기기" /><br/><b>마감 앞당기기</b><br/><sub>판매자가 남길 시간을 지정해 마감 시간을 조정합니다</sub></td>
  </tr>
  <tr>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/0e08058d-a615-4591-b35b-0fe29342be0d" width="230" alt="종료 결과" /><br/><b>종료 결과</b><br/><sub>낙찰 및 유찰 건수와 총 낙찰 금액을 정리합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/09df720d-ffd2-4d72-b61c-b3a00e6a878f" width="230" alt="낙찰 후보" /><br/><b>낙찰 후보</b><br/><sub>낙찰 후보의 순위와 차순위 승계를 관리합니다</sub></td>
    <td width="33%" align="center"><img src="https://github.com/user-attachments/assets/74c56aa4-e01f-433e-918b-51bf8de09f0d" width="230" alt="거래 완료" /><br/><b>거래 완료</b><br/><sub>판매자와 구매자 사이에 성사된 거래를 기록합니다</sub></td>
  </tr>
</table>

</details>

<br>

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| **Backend** | ![Java 21](https://img.shields.io/badge/Java%2021-007396?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot 4.x](https://img.shields.io/badge/Spring%20Boot%204.x-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=flat-square&logo=spring&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL-0769AD?style=flat-square) |
| **Database** | ![MySQL 8.4](https://img.shields.io/badge/MySQL%208.4-4479A1?style=flat-square&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-FF4438?style=flat-square&logo=redis&logoColor=white) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white) |
| **실시간 통신** | ![SSE](https://img.shields.io/badge/SSE-5B21B6?style=flat-square) ![Redis Pub%2FSub](https://img.shields.io/badge/Redis%20Pub%2FSub-FF4438?style=flat-square&logo=redis&logoColor=white) ![Redis Stream](https://img.shields.io/badge/Redis%20Stream-FF4438?style=flat-square&logo=redis&logoColor=white) |
| **Frontend** | ![React 19](https://img.shields.io/badge/React%2019-61DAFB?style=flat-square&logo=react&logoColor=black) ![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white) ![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white) ![TanStack Router%2FQuery](https://img.shields.io/badge/TanStack%20Router%2FQuery-FF4154?style=flat-square&logo=reactquery&logoColor=white) ![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white) ![shadcn%2Fui](https://img.shields.io/badge/shadcn%2Fui-000000?style=flat-square&logo=shadcnui&logoColor=white) ![Orval](https://img.shields.io/badge/Orval-1E1E1E?style=flat-square) |
| **Infra** | ![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white) ![EC2](https://img.shields.io/badge/EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white) ![RDS](https://img.shields.io/badge/RDS-527FFF?style=flat-square&logo=amazonrds&logoColor=white) ![S3](https://img.shields.io/badge/S3-569A31?style=flat-square&logo=amazons3&logoColor=white) ![CloudFront](https://img.shields.io/badge/CloudFront-8C4FFF?style=flat-square&logo=amazoncloudfront&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white) ![Nginx](https://img.shields.io/badge/Nginx-009639?style=flat-square&logo=nginx&logoColor=white) ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white) |
| **관측** | ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white) ![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white) ![Loki](https://img.shields.io/badge/Loki-F46800?style=flat-square&logo=grafana&logoColor=white) ![Slack](https://img.shields.io/badge/Slack-4A154B?style=flat-square&logo=slack&logoColor=white) |
| **Test · 측정** | ![JUnit 5](https://img.shields.io/badge/JUnit%205-25A162?style=flat-square&logo=junit5&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-291A54?style=flat-square&logo=testcontainers&logoColor=white) ![k6](https://img.shields.io/badge/k6-7D64FF?style=flat-square&logo=k6&logoColor=white) |

기술을 선택한 이유는 Wiki의 [고른 것들](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-기술-스택)과 [기술 선택의 변화](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/회고-기술-선택의-변화)에 정리했습니다.

<br>

## 시스템 구성

<img width="5136" height="1920" alt="image (1)" src="https://github.com/user-attachments/assets/5a377051-dc0c-4878-b49b-5285655bace2" />


### 데이터 모델

![ERD](https://github.com/user-attachments/assets/da9e92ab-a424-4625-ba80-852f8491a217)

ERD 원본은 [ERDCloud](https://www.erdcloud.com/d/4FRa83M5MbkZsMeYY)에서 확인할 수 있습니다.

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

대부분의 도메인은 `controller → service → repository` 구조를 따릅니다.

<br>

## 관련 문서

### 기술 문서

기술 문서는 [GitHub Wiki](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki)에 정리했습니다. 구현 결과뿐 아니라 기술을 선택한 이유와 변경 과정도 함께 기록하고 있습니다.

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

### 주요 링크

| 구분 | 링크 |
| --- | --- |
| **서비스** | <https://www.upbid.store> |
| **API 명세** | <https://api.upbid.store/swagger-ui/index.html> (Swagger UI) |
| **ERD** | <https://www.erdcloud.com/d/4FRa83M5MbkZsMeYY> (ERDCloud) |
| **기획안** | <https://amethyst-naranja-aac.notion.site/UpBid-3a64960319768083b497e9ef3596939c> (Notion) |

<br>

## 팀 구성

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

구체적인 팀원 및 역할은 Wiki의 [팀원 및 역할](https://github.com/softeerbootcamp-8th/WEB-Team6-Hot6ix/wiki/Project-팀원-및-역할)에서 확인할 수 있습니다.
