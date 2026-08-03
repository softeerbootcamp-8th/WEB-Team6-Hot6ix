# 프론트엔드 배포 — S3 + CloudFront

프론트를 AWS S3(정적 파일) + CloudFront(CDN·HTTPS·SPA 라우팅)로 배포하는 절차입니다.
백엔드는 이미 `https://api.upbid.store` 로 떠 있고, 이 문서는 **프론트만** 다룹니다.

- 매 배포는 [`.github/workflows/CICD-FE.yml`](../../.github/workflows/CICD-FE.yml) 이 합니다.
- 인프라 최초 생성(S3·CloudFront)은 AWS 콘솔에서 수동으로 했습니다(4-2 참고).

> **이 문서의 상태: 제안입니다.** 아래 0장의 항목은 팀이 정할 사안이고, 지금 값은
> 문서를 이어 쓰기 위한 가정입니다. 합의 전에는 확정된 규칙으로 읽지 마세요.
> 코드·스크립트·워크플로도 그 가정 위에 쓰여 있어, 결정이 바뀌면 같이 고쳐야 합니다.

---

## 0. 팀이 정해야 하는 것

| # | 항목 | 이 문서의 가정 | 대안 | 바뀌면 고칠 곳 |
| --- | --- | --- | --- | --- |
| 1 | 프론트 도메인 | `www.upbid.store` | apex(`upbid.store`) — DNS 를 Route 53 으로 이전해야 함 / CloudFront 기본 도메인 — **로그인 불가**(2장) | ACM 인증서, 가비아 CNAME, 백엔드 `FRONTEND_URL` |
| 2 | API 경로 | 브라우저 → `api.upbid.store` 직접 | CloudFront 에 `/api/*` 비헤이비어를 두고 단일 오리진 (CORS·preflight 없음) | CloudFront 배포 설정, `VITE_API_BASE_URL` |
| 3 | 배포 시 옛 자산 처리 | 지우지 않음(`--delete` 없음) | 매 배포마다 정리 — 진행 중 세션이 깨질 수 있음(7장) | 워크플로 sync 단계 |
| 4 | CI 자격증명 | ~~IAM 액세스 키 + Secrets~~ → **GitHub OIDC 역할로 결정**(4-3) | — | 결정됨 |
| 5 | `routeTree.gen.ts` | gitignore 유지, CI 에서 생성 | 커밋 대상으로 전환(TanStack 권장) | `.gitignore`, `build` 스크립트, 6장 |
| 6 | 신규 가입 리다이렉트 | 미해결 — 아래 5장 | 백엔드가 `/signup/phone` 로 보내기 / 프론트에 `/onboarding` 추가 | 공용 계약이라 양쪽 합의 필요 |

2장의 **쿠키 same-site 제약은 기술적 사실**이라 선택지가 아닙니다. 그 제약 안에서
어떤 도메인을 쓸지가 1번 결정입니다.

## 1. 구조

```
                 ┌──────────────────────────────────────┐
브라우저 ────────►│ CloudFront  (https, gzip/br, SPA 폴백) │
                 └───────────────┬──────────────────────┘
                                 │ OAC (서명된 내부 요청)
                                 ▼
                    ┌────────────────────────┐
                    │ S3  upbid-frontend      │  ← 비공개. 웹사이트 호스팅 끔
                    │  index.html             │
                    │  assets/*-<hash>.js|css │
                    └────────────────────────┘

브라우저 ──────────► https://api.upbid.store  (EC2 nginx → Spring)   ※ CloudFront 를 거치지 않음
```

위 그림의 각 선택과 근거입니다. 0장 표의 번호가 붙은 것은 **아직 제안**입니다.

| 구성 | 근거 | 상태 |
| --- | --- | --- |
| S3 는 **비공개** + CloudFront **OAC** | 버킷을 공개하지 않아도 된다. S3 정적 웹사이트 호스팅은 HTTPS 를 못 붙인다 | 사실상 표준 |
| 해시 자산은 1년 `immutable`, `index.html` 은 `no-cache` | 파일명에 해시가 있으니 내용이 바뀌면 이름이 바뀐다. 진입점만 매번 새로 받으면 된다 | 사실상 표준 |
| SPA 폴백은 CloudFront 커스텀 오류 응답 | OAC + 비공개 버킷에서 없는 키는 403 으로 온다(8장) | 사실상 표준 |
| 프론트 도메인은 `upbid.store` 의 서브도메인 | 세션 쿠키 same-site 제약(2장) | **제약은 사실, 도메인은 결정 1** |
| API 는 CloudFront 를 거치지 않는다 | 3장 | **결정 2** |
| 배포 시 옛 자산을 지우지 않는다 | 라우트별 코드 분할 때문(7장) | **결정 3** |

## 2. 도메인 — 왜 `www.upbid.store` 인가 (중요)

인증이 **세션 쿠키(`SESSION`, HttpOnly)** 라서 프론트 도메인을 아무 데나 두면
**화면은 떠도 로그인이 안 됩니다.** 쿠키의 same-site 판정은 호스트가 아니라
**등록 가능 도메인(eTLD+1)** 기준입니다.

| 프론트 주소 | API 주소 | 관계 | 세션 쿠키 |
| --- | --- | --- | --- |
| `https://www.upbid.store` | `https://api.upbid.store` | cross-origin 이지만 **same-site** (eTLD+1 이 둘 다 `upbid.store`) | `SameSite=Lax` 로 그대로 실린다 ✅ |
| `https://dxxxx.cloudfront.net` | `https://api.upbid.store` | **cross-site** | `SameSite=None; Secure` 필요. iOS Safari(ITP)·서드파티 쿠키 차단에 걸려 로그인 실패 ❌ |

QR·링크로 들어오는 모바일 유입이 주 경로인 서비스라 두 번째 안은 쓸 수 없습니다.
**CloudFront 기본 도메인(`dxxxx.cloudfront.net`)은 "정적 호스팅이 붙었는지"
확인용으로만 쓰고, 로그인까지 확인하는 배포는 반드시 `*.upbid.store` 로 합니다.**

### apex(`upbid.store`)를 쓰려면

현재 `upbid.store` 의 DNS 는 **가비아**에 있습니다(`ns.gabia.net`).

- apex 에는 CNAME 을 걸 수 없고(DNS 규격), CloudFront 는 고정 IP 가 없어 A 레코드도 안 됩니다.
- 그래서 지금 선택할 수 있는 건 둘입니다.

| 안 | 내용 | 비용 |
| --- | --- | --- |
| **A (권장, 지금)** | `www.upbid.store` 를 CNAME 으로 CloudFront 에 연결. apex 는 기존 nginx 에서 `301 → https://www.upbid.store` | 가비아 DNS 레코드 1개 + apex 용 Let's Encrypt 인증서 1장 |
| B (나중) | NS 를 Route 53 으로 위임하고 apex ALIAS 로 CloudFront 연결 | DNS 이전 작업. 잘못되면 `api.upbid.store` 까지 같이 죽으므로 별도 이슈로 진행 |

공유 링크가 `upbid.store/join/<code>` 처럼 짧아야 한다면 B 가 맞습니다. 다만
운영 중인 API 도메인이 같은 존에 있어 스프린트 중 NS 이전은 위험 부담이 있습니다.
**어느 쪽으로 갈지는 팀 결정(0장 1번)이고, 이 문서는 이어 쓰기 위해 A 를 가정합니다.**

## 3. API 경로 — 이 문서가 가정한 쪽 (결정 2)

CloudFront 에 `/api/*` 비헤이비어를 추가해 단일 오리진으로 만들면 CORS 와
preflight 가 사라집니다. 그런데도 이 문서는 직접 호출을 가정했습니다. 근거는
아래 세 가지이고, 팀 판단이 다르면 바꿔야 합니다.

- **실시간 전송 방식이 아직 미확정입니다.** `src/features/live/use-realtime-status.ts`
  는 상태 기계만 있고 SSE/WebSocket 이 붙지 않았습니다. CloudFront 를 장수 연결
  앞에 두면 오리진 응답 타임아웃(기본 30초, 상향해도 60초)과 스트림 버퍼링을
  따로 다뤄야 합니다. 전송 방식이 정해지기 전에 그 복잡도를 먼저 지고 갈 이유가 없습니다.
- 백엔드 CORS 는 이미 `app.frontend-url` 로 파라미터화돼 있어(`WebMvcConfig`)
  환경변수 한 줄로 끝납니다.
- same-site 라 쿠키는 문제없고, 추가 비용은 preflight(OPTIONS) 왕복뿐입니다.

실시간 전송이 확정되고 preflight 지연이 실제로 문제가 되면 그때 재검토합니다
(그때는 `/api/*` 비헤이비어 + `Managed-CachingDisabled` +
`Managed-AllViewerExceptHostHeader` 조합).

## 4. 사전 준비 (1회)

### 4-1. ACM 인증서 — **반드시 us-east-1**

CloudFront 는 다른 리전의 인증서를 받지 않습니다. 서울 리전에 만들면 목록에 안 뜹니다.

```bash
aws acm request-certificate \
  --domain-name www.upbid.store \
  --validation-method DNS \
  --region us-east-1

# 검증용 CNAME 이름·값 확인
aws acm describe-certificate --region us-east-1 \
  --certificate-arn <위에서 받은 ARN> \
  --query 'Certificate.DomainValidationOptions[].ResourceRecord'
```

출력된 CNAME 을 **가비아 DNS 관리**에 그대로 등록하면 몇 분 뒤 `ISSUED` 가 됩니다.
발급 전에는 CloudFront 에 붙일 수 없습니다.

### 4-2. 인프라 생성 — **콘솔에서 수동으로 했다**

CLI 자동화 스크립트(`provision-aws.sh`)를 만들어뒀었지만, 이 작업에 쓴 AWS
계정은 로컬 자격증명으로 `s3:CreateBucket`·`cloudfront:*` 가 SCP로 막혀 있어
([[local-aws-creds-cannot-provision]]) 애초에 실행할 수 없었습니다. 실제로는
AWS 콘솔에서 아래 순서로 직접 만들었고, 안 쓴 스크립트는 삭제했습니다.

1. S3 버킷 생성(`upbid-frontend`) — 퍼블릭 액세스 차단 유지
2. CloudFront 배포 생성 — 오리진을 그 버킷으로, **Origin access control(OAC)** 사용
3. 커스텀 오류 응답 추가 — 403·404 모두 `/index.html` + HTTP 200 (8장, SPA 폴백)
4. HTTP → HTTPS 리다이렉트, 압축(gzip/br) 옵션 켜기
5. (인증서 발급 후) Alternate domain name 에 `www.upbid.store` 추가 + 인증서 연결(2장)

배포가 전 엣지에 퍼지기까지 **5~15분** 걸립니다. 다른 계정·리전에 다시 만들
일이 있으면 이 순서를 그대로 따라 하면 됩니다.

### 4-3. 배포용 자격증명 — **GitHub OIDC 역할** (액세스 키 아님)

**액세스 키로는 CD 가 불가능합니다.** 조직 SCP(`p-ibyqe45g`)가 **MFA 없는
요청**에 대해 `s3:*`, `cloudfront:*`, `iam:*` 를 explicit deny 합니다. IAM
사용자의 장기 액세스 키에는 MFA 컨텍스트가 없어서, 키를 GitHub Secrets 에
넣어도 배포가 첫 단계(`aws s3 sync` 의 `s3:ListBucket`)에서 죽습니다.

```
AccessDenied ... s3:ListBucket ... with an explicit deny in a service control
policy: arn:aws:organizations::652613583830:policy/.../p-ibyqe45g
```

같은 SCP 때문에 로컬 CLI 로는 IAM 을 아예 손댈 수 없습니다(`iam:CreateRole` 까지
deny). **콘솔 로그인은 MFA 를 타므로 통과**하고, 그래서 아래 역할 생성은
반드시 **콘솔에서** 합니다. (S3·CloudFront 도 이 경로로 만들어졌습니다.)

| 항목 | 값 |
| --- | --- |
| 역할 이름 | **`GitHubActionsRole`** — 운영자가 지정한 이름. SCP 예외가 이 이름에 걸려 있으므로 **오타·변경 금지** |
| 신뢰 정책 | [`../deploy/github-actions-trust-policy.json`](../deploy/github-actions-trust-policy.json) — `main` 브랜치에서 온 토큰만 허용 |
| 권한 정책 | [`../deploy/ci-iam-policy.json`](../deploy/ci-iam-policy.json) — 그 버킷·그 배포만 (인라인으로 붙임) |

절차(콘솔):

1. IAM → **자격 증명 공급자**에 `token.actions.githubusercontent.com` 이 있는지
   확인. 이 계정에는 이미 있습니다(기존 `github-actions-role` 이 이 공급자를
   신뢰합니다). 없으면 **공급자 추가 → OpenID Connect**, URL
   `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`
2. IAM → 역할 → **역할 생성** → **사용자 지정 신뢰 정책** →
   `github-actions-trust-policy.json` 내용 붙여넣기
3. 권한은 **인라인 정책**으로 `ci-iam-policy.json` 내용 붙여넣기
4. 역할 이름 `GitHubActionsRole` 로 생성

> **남은 위험:** SCP 의 조건이 `aws:MultiFactorAuthPresent` 라면 OIDC 로 맡은
> 역할 세션도 이 값이 `false` 입니다. 운영자가 이 역할 이름을 deny 대상에서
> 예외로 뺐다는 전제이며, 그렇지 않으면 **역할을 만들어도 같은 SCP 에 막힙니다.**
> 첫 `workflow_dispatch` 실행에서 확인하고, 또 막히면 위 에러 메시지를 그대로
> 운영자에게 전달해 예외 조건을 확인받습니다.
> 그때까지의 임시 배포 수단은 [`../deploy/deploy-manual.sh`](../deploy/deploy-manual.sh)
> (사람이 MFA 코드를 넣어 로컬에서 올리는 스크립트)입니다.

### 4-4. GitHub Secrets

`Settings → Secrets and variables → Actions` 에 3개를 넣습니다.

| 이름 | 값 |
| --- | --- |
| `AWS_ROLE_ARN` | `arn:aws:iam::603224628947:role/GitHubActionsRole` (4-3) |
| `FE_S3_BUCKET` | `upbid-frontend` |
| `FE_CLOUDFRONT_DISTRIBUTION_ID` | `E17BE3ZTNT6VTF` |

`AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY` 는 **더 이상 쓰지 않습니다.**
OIDC 로 전환한 뒤에는 GitHub 에서 지우고, AWS 콘솔에서 그 액세스 키도
비활성화·삭제하세요(SCP 때문에 배포에는 쓸 수 없고, 남겨 두면 유출 위험만
남습니다).

단, **로컬 `~/.aws` 의 키는 남겨 둡니다.** MFA 세션을 발급받아 수동 배포하는
`deploy-manual.sh` 가 그 키를 씁니다.

`VITE_API_BASE_URL` 은 Secrets 가 아니라 워크플로 `env:` 에 평문으로 둡니다.
프론트 번들에 그대로 박히는 값이라 숨겨도 의미가 없습니다.

### 4-5. DNS (가비아)

| 타입 | 호스트 | 값 |
| --- | --- | --- |
| CNAME | `www` | `dxxxx.cloudfront.net` (끝의 `.` 포함) |

apex 리다이렉트는 기존 nginx(`3.36.33.153`)에서 처리합니다. 지금 apex 는
인증서가 없어 `https://upbid.store` 가 TLS 에서 끊기므로, apex 용 인증서를
먼저 받아야 리다이렉트가 의미가 있습니다.

```bash
# EC2 에서
sudo certbot --nginx -d upbid.store
# server_name upbid.store; → return 301 https://www.upbid.store$request_uri;
```

## 5. 백엔드도 함께 바꿔야 합니다 (안 하면 로그인 실패)

프론트만 배포하면 **화면은 뜨지만 로그인·모든 API 가 막힙니다.** CORS 허용
오리진과 카카오 로그인 후 착지 주소가 둘 다 백엔드의 `app.frontend-url`
한 값에서 나오기 때문입니다(`WebMvcConfig`, `OAuthController`).

| 환경변수 | 값 | 쓰이는 곳 |
| --- | --- | --- |
| `FRONTEND_URL` | `https://www.upbid.store` | CORS `allowedOrigins`, 로그인 후 `sendRedirect` 대상 |
| `SESSION_COOKIE_SECURE` | `true` | HTTPS 전용 쿠키 |
| `SESSION_COOKIE_SAME_SITE` | `lax` | 기본값과 같지만 명시 |

> **끝에 `/` 를 붙이지 마세요.** CORS 오리진 비교는 문자열 완전일치이고,
> 리다이렉트는 `frontendUrl + "/onboarding"` 으로 이어 붙으므로 `//onboarding`
> 이 됩니다.

### 확인이 필요한 지점

`backend/deploy/docker-compose.yml` 은 현재 컨테이너에 datasource 3개와
`SPRING_PROFILES_ACTIVE` 만 넘깁니다. 그런데 `application.yaml` 의
`KAKAO_CLIENT_ID`·`NCP_*` 는 기본값이 없어서 없으면 애플리케이션이 기동조차
못 합니다. 즉 **운영 서버는 compose 밖의 다른 경로로 이 값들을 받고 있습니다**
(서버에 직접 둔 `.env`, 또는 저장소와 다른 compose).

그 경로를 아는 사람이 `FRONTEND_URL` 을 같은 자리에 넣어야 합니다. 경로를
확인하지 않은 채 compose 만 고치면 운영 백엔드가 재기동에 실패할 수 있어,
이 문서는 변경안만 적어 둡니다.

```yaml
# backend/deploy/docker-compose.yml — compose 로 관리하는 것이 맞다면
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://${RDS_ENDPOINT}:3306/upbid
      - SPRING_DATASOURCE_USERNAME=${RDS_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${RDS_PASSWORD}
      - SPRING_PROFILES_ACTIVE=prod
      - FRONTEND_URL=${FRONTEND_URL}
      - SESSION_COOKIE_SECURE=true
      - SESSION_COOKIE_SAME_SITE=lax
```

### 카카오 개발자 콘솔

코드 교환을 백엔드가 하므로 Redirect URI 는
`https://api.upbid.store/api/v1/oauth/kakao/callback` 그대로입니다. **콘솔 변경은
필요 없습니다.** 로그인 후 착지 주소만 위 `FRONTEND_URL` 로 바뀝니다.

### 함께 정리할 계약 불일치

`OAuthController` 는 신규 사용자를 `FRONTEND_URL + "/onboarding"` 으로 보내는데
프론트에 `/onboarding` 라우트가 없습니다(가입 흐름은 `/signup/phone`).
지금은 로그인이 목업이라 드러나지 않지만, 실제 카카오 로그인을 붙이는 순간
신규 가입자는 전역 404 를 봅니다. 백엔드가 `/signup/phone` 으로 보내거나
프론트가 `/onboarding` 라우트를 추가해야 하고, **어느 쪽을 고칠지는 팀이
정할 사안**입니다(공용 계약).

## 6. 배포 흐름

| 트리거 | 하는 일 |
| --- | --- |
| `main`·`dev` 로 향하는 PR (`frontend/**` 변경) | 포맷 확인 → lint → 빌드 → 타입 검사 |
| `main` push (`frontend/**` 변경) | 빌드 → 타입 검사 → S3 업로드 → CloudFront 무효화 |
| Actions 탭의 **Run workflow** | 커밋 없이 재배포 (버튼은 기본 브랜치 기준으로 노출) |

팀 배포 주기(매일 오전 10시 `dev → main` 머지)에 프론트도 같이 따라갑니다.

### `pnpm build` 를 CI 에서 그대로 쓰지 않는 이유

`src/routeTree.gen.ts` 는 gitignore 대상이고, 이 파일을 만드는 주체는 vite 의
라우터 플러그인입니다. 그런데 `pnpm build` 는 `tsc -b && vite build` 라서 새
체크아웃에서는 **타입 검사가 먼저 돌아 라우트 타입을 못 찾고 실패합니다**
(`error TS2345: ... is not assignable to parameter of type 'undefined'` 24건).
그래서 워크플로는 `vite build` → `tsc -b` 순서로 나눠 실행합니다.

> 로컬에서도 새로 클론한 직후 `pnpm build` 를 먼저 실행하면 같은 오류가 납니다.
> `pnpm dev` 를 한 번 띄우면 생성됩니다. 근본적으로는 `build` 스크립트를
> `vite build && tsc -b` 로 바꾸거나 `routeTree.gen.ts` 를 커밋 대상으로
> 돌리는 편이 맞지만, 둘 다 문서화된 팀 규칙이라 별도로 합의합니다.

## 7. 캐시 전략

| 대상 | `Cache-Control` | 이유 |
| --- | --- | --- |
| `assets/*-<hash>.js`, `.css` | `public,max-age=31536000,immutable` | 내용이 바뀌면 파일명이 바뀐다 |
| `index.html` | `no-cache` | 진입점. 새 자산을 가리켜야 한다 |

업로드 순서도 의미가 있습니다. **자산 → `index.html`** 순서라야 새 `index.html`
이 참조하는 청크가 이미 S3 에 있습니다.

현재 워크플로는 **`--delete` 를 쓰지 않습니다(결정 3).** 라우트별 코드 분할
(`autoCodeSplitting`) 때문에, 지금 열려 있는 탭이 예전 `index.html` 을 들고
있는데 예전 청크를 지우면 그 사용자가 화면을 이동하는 순간 청크 404 로 앱이
깨집니다. 경매 진행 중이면 입찰을 놓칩니다. 쌓이는 양은 수 MB 수준이라
그대로 두는 쪽을 가정했고, 정리가 필요하면 배포가 없는 시간에 수동으로 합니다.

### 배포 전에 확인할 위험 — 비ASCII 청크 파일명

`pnpm exec vite build` 산출물에 **한글 파일명 청크가 하나 있습니다.**

```
dist/assets/경매방-DqoPLi9a.js      # join/$shareCode, rooms/$roomId, seller/rooms/$roomId/created 가 동적 import
```

이름의 출처는 `src/api/generated/경매방/` 이고, 그 폴더명은 orval 이 백엔드
springdoc 의 `@Tag`(한글)에서 만듭니다. 즉 **백엔드 태그 이름이 프론트 청크
파일명이 되고, 그게 S3 오브젝트 키가 됩니다.**

- S3 키와 CloudFront 는 UTF-8 키를 지원하고, 브라우저는 요청 시
  `%EA%B2%BD...` 로 퍼센트 인코딩해 보냅니다. 보통은 그대로 동작합니다.
- 다만 이 경로가 깨지면 **경매방 화면 3개가 통째로 못 열립니다.** 서비스의 핵심
  화면이고, 로컬 `pnpm preview` 로는 S3·CloudFront 의 인코딩 처리를 검증할 수
  없습니다. 그래서 **첫 배포 직후 9장 검증에서 이 청크를 반드시 직접 확인**해야
  합니다.

애초에 ASCII 로 고정하고 싶다면 `vite.config.ts` 에 아래를 더하는 방법이 있습니다.
빌드 산출물 이름이 팀 전체에 영향을 주므로 합의 후에 적용하세요.

```ts
build: {
  rollupOptions: {
    output: {
      // 한글 청크명이 S3 키·URL 인코딩을 타지 않게 ASCII 로 고정한다.
      sanitizeFileName: (name) => name.replace(/[^\w.-]/g, '_'),
    },
  },
},
```

## 8. SPA 라우팅

`/rooms/1` 은 S3 에 파일이 없습니다. 비공개 버킷 + OAC 조합에서 없는 키는
404 가 아니라 **403(AccessDenied)** 으로 돌아오므로, CloudFront 커스텀 오류
응답에서 **403 과 404 를 모두** `/index.html` + `200` 으로 바꿉니다
(`ErrorCachingMinTTL=0`). 이후 라우팅은 TanStack Router 가 클라이언트에서 합니다.

403 만 넣거나 상태 코드를 200 으로 바꾸지 않으면 새로고침·QR 직접 진입에서
빈 화면이나 브라우저 기본 오류가 뜹니다.

## 9. 배포 후 검증

```bash
FE=https://www.upbid.store

# 1) 진입점: 200 + no-cache
curl -sI $FE | grep -iE '^(HTTP|cache-control|x-cache)'

# 2) 해시 자산: 1년 immutable + 엣지 히트
ASSET=$(curl -s $FE | grep -o '/assets/index-[^"]*\.js' | head -1)
curl -sI "$FE$ASSET" | grep -iE '^(HTTP|cache-control|content-encoding|x-cache)'

# 3) SPA 딥링크: 200 (404 아님)
curl -sI $FE/rooms/1 | head -1
curl -sI $FE/join/abc123 | head -1

# 4) HTTP → HTTPS 강제
curl -sI http://www.upbid.store | head -1     # 301

# 5) 한글 청크 (7장의 위험) — 200 이어야 한다. 404·403 이면 경매방 화면이 안 열린다
curl -sI --path-as-is "$FE/assets/%EA%B2%BD%EB%A7%A4%EB%B0%A9-DqoPLi9a.js" | head -1
#   해시는 빌드마다 바뀐다. 실제 이름은 아래로 확인
#   aws s3 ls s3://upbid-frontend/assets/ | grep -v '^.*[[:space:]][a-zA-Z0-9._-]*$'
```

브라우저에서 확인할 것:

1. `/rooms/1` 을 주소창에 직접 입력 → **새로고침해도** 화면이 뜬다
2. 모바일 폭(390px)에서도 같은 화면 — 데스크톱/모바일은 CSS 가 아니라 다른 트리다
3. 카카오 로그인 → 개발자도구 Application → Cookies 에 `api.upbid.store` 의
   `SESSION` 이 있고 `Secure`·`SameSite=Lax` 다
4. 로그인 후 `/api/v1/**` 요청이 200 (401·CORS 오류가 아니다)
5. Network 탭에 `use-realtime-status` 청크 404 같은 로드 실패가 없다

## 10. 롤백

CloudFront·S3 에는 버전 개념이 없으므로 **되돌리려면 이전 커밋을 다시
배포**합니다.

1. Actions → `CICD - Frontend` → 마지막으로 정상이던 실행 → **Re-run all jobs**
2. 또는 `main` 을 정상 커밋으로 되돌리는 PR 을 만들어 머지(`git push --force` 금지)

`index.html` 이 `no-cache` 라서 무효화가 끝나면 즉시 반영됩니다. 옛 자산을
지우지 않으므로 이전 버전의 청크도 그대로 살아 있습니다.

## 11. 자주 나는 문제

| 증상 | 원인·해결 |
| --- | --- |
| 화면은 뜨는데 API 가 전부 401 | 백엔드 `FRONTEND_URL` 이 안 바뀜(5장). 또는 프론트를 CloudFront 기본 도메인으로 접속(2장) |
| 콘솔에 CORS 오류 | `FRONTEND_URL` 과 실제 접속 오리진이 정확히 같아야 한다. 끝의 `/`, `www` 유무, `http`/`https` 까지 |
| 새로고침하면 흰 화면 / 403 | CloudFront 커스텀 오류 응답 미설정(8장) |
| 배포했는데 예전 화면 | 무효화 실패 또는 `index.html` 에 긴 캐시가 붙음. 응답 헤더의 `cache-control` 확인 |
| 화면 이동 시 청크 404 | 배포에 `--delete` 가 붙었는지 확인(7장) |
| ACM 인증서가 CloudFront 목록에 없음 | 리전이 us-east-1 이 아니다(4-1) |
| 로그인 후 404 화면 | 신규 사용자 `/onboarding` 리다이렉트. 5장의 계약 불일치 |
| CI 에서 `TS2345 ... type 'undefined'` | 라우트 트리 생성 전에 `tsc` 가 돌았다(6장) |
| `Credentials could not be loaded` | deploy 잡에 `permissions: id-token: write` 가 없다(4-3) |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | 신뢰 정책의 `sub` 와 실제 브랜치가 다르다. `main` 에서만 맡을 수 있다 |
| `AccessDenied ... explicit deny in a service control policy` | 액세스 키로 배포하고 있거나, SCP 가 `GitHubActionsRole` 을 예외로 두지 않았다(4-3) |
| 경매방·공유링크 화면만 안 열림 | 한글 청크(`경매방-*.js`) 로드 실패. 7장의 비ASCII 파일명 |
