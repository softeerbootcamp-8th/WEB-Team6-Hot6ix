#!/usr/bin/env bash
#
# 프론트엔드 수동 배포 — MFA 세션으로 S3 업로드 + CloudFront 무효화
#
# 왜 이게 필요한가:
#   조직 SCP 가 MFA 없는 요청에 대해 s3:*, cloudfront:* 를 explicit deny 한다.
#   GitHub Actions 는 MFA 를 제시할 수 없어 CD(deploy 잡)가 통째로 막힌다.
#   그래서 배포는 사람이 MFA 코드를 넣어 이 스크립트로 한다.
#   CD 가 가능해지면(팀 공용 계정 등) 이 스크립트는 필요 없어진다.
#
# 사용법:
#   ./deploy/deploy-manual.sh <MFA 6자리 코드>
#
# 주의: MFA 코드는 30초마다 바뀐다. 실행 직전에 확인해서 넣는다.

set -euo pipefail

MFA_SERIAL="arn:aws:iam::603224628947:mfa/My_Phone"
BUCKET="upbid-frontend"
DISTRIBUTION_ID="E17BE3ZTNT6VTF"
API_BASE_URL="https://api.upbid.store"
FE_URL="https://www.upbid.store"
# src/components/motion-sources.ts 의 MOTION_BASE 와 같은 값이어야 한다.
MOTION_VERSION="v3"
export AWS_DEFAULT_REGION="ap-northeast-2"

cd "$(dirname "$0")/.."

if [ $# -lt 1 ]; then
  echo "사용법: $0 <MFA 6자리 코드>" >&2
  exit 1
fi
MFA_CODE="$1"

# ── 1. MFA 세션 발급 ────────────────────────────────────────────────
# 이 임시 자격증명에는 MFA 컨텍스트가 있어 SCP 를 통과한다. 기본 12시간 유효.
echo "▶ MFA 세션 발급 중..."
CREDS=$(aws sts get-session-token \
  --serial-number "$MFA_SERIAL" \
  --token-code "$MFA_CODE" \
  --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \
  --output text)

export AWS_ACCESS_KEY_ID=$(echo "$CREDS" | cut -f1)
export AWS_SECRET_ACCESS_KEY=$(echo "$CREDS" | cut -f2)
export AWS_SESSION_TOKEN=$(echo "$CREDS" | cut -f3)

# ── 2. 권한 확인 (업로드 전에 먼저 막히는지 본다) ──────────────────
# 여기서 AccessDenied 가 나면 SCP 조건이 MFA 가 아니라는 뜻이다.
# 아무것도 올리지 않은 상태로 멈추는 편이 안전하다.
echo "▶ 버킷 접근 확인..."
if ! aws s3 ls "s3://$BUCKET/" >/dev/null 2>&1; then
  echo "✗ MFA 세션으로도 s3:ListBucket 이 거부됐다." >&2
  echo "  SCP 조건이 MFA 가 아니다. 조직 관리자에게 정책 확인이 필요하다." >&2
  aws s3 ls "s3://$BUCKET/" 2>&1 | head -3 >&2
  exit 1
fi
echo "  ok"

# ── 2-1. 연출 APNG 존재 확인 ────────────────────────────────────────
# 이 셋은 저장소에 없고 손으로 S3 에 올린 것을 쓴다
# (src/components/motion-sources.ts). 빌드 산출물이 아니라서 배포가 만들어
# 주지 않으므로, 없는 채로 코드만 올리면 라이브 경매방 연출이 통째로 정지
# 이미지로 떨어진다. 그 상태를 배포하고 나서 알아차리는 일이 없도록
# 여기서 먼저 막는다.
echo "▶ 연출 APNG 확인 (motion/$MOTION_VERSION)..."
MISSING=0
for name in auction-cat-sold soft-close-extended auction-start; do
  if aws s3 ls "s3://$BUCKET/motion/$MOTION_VERSION/$name.png" >/dev/null 2>&1; then
    echo "  ok   $name.png"
  else
    echo "  없음 $name.png" >&2
    MISSING=1
  fi
done
if [ "$MISSING" = "1" ]; then
  echo "✗ 연출 APNG 가 S3 에 없다. 배포를 중단한다." >&2
  echo "  콘솔에서 s3://$BUCKET/motion/$MOTION_VERSION/ 에 올린 뒤 다시 실행한다." >&2
  echo "  백업: ~/hot6ix-personal-notes/motion-assets/" >&2
  exit 1
fi

# ── 3. 빌드 ─────────────────────────────────────────────────────────
# pnpm build(= tsc -b && vite build)를 쓰지 않는다. routeTree.gen.ts 가
# gitignore 대상이고 이 파일을 만드는 주체가 vite 라, 새 체크아웃에서는
# tsc 가 먼저 돌면 라우트 타입을 못 찾고 실패한다.
echo "▶ 빌드..."
rm -rf dist
VITE_API_BASE_URL="$API_BASE_URL" pnpm exec vite build
pnpm exec tsc -b
echo "  ok"

# ── 4. 자산 업로드 (해시 파일명 → 1년 immutable) ────────────────────
# 순서가 중요하다. 자산 → index.html 이라야 새 index.html 이 참조하는
# 청크가 이미 S3 에 있다.
#
# --delete 를 쓰지 않는다. 라우트별 코드 분할 때문에, 지금 열려 있는 탭이
# 예전 index.html 을 들고 있는데 예전 청크를 지우면 화면 이동 시 404 로
# 앱이 깨진다. 경매 진행 중이면 입찰을 놓친다.
echo "▶ 자산 업로드..."
aws s3 sync dist "s3://$BUCKET" \
  --exclude 'index.html' \
  --cache-control 'public,max-age=31536000,immutable' \
  --no-progress

# ── 5. index.html 업로드 (캐시 금지) ────────────────────────────────
echo "▶ index.html 업로드..."
aws s3 cp dist/index.html "s3://$BUCKET/index.html" \
  --cache-control 'no-cache' \
  --content-type 'text/html; charset=utf-8' \
  --no-progress

# ── 6. CloudFront 무효화 ────────────────────────────────────────────
# SPA 라우트는 커스텀 오류 응답(403/404 → /index.html)으로 서비스되고 그
# 응답도 경로별로 캐시된다. index.html 만 무효화하면 /rooms/1 같은 경로가
# 예전 문서를 계속 준다. /* 무효화는 월 1000 경로까지 무료다.
echo "▶ CloudFront 무효화..."
INVALIDATION_ID=$(aws cloudfront create-invalidation \
  --distribution-id "$DISTRIBUTION_ID" \
  --paths '/*' \
  --query 'Invalidation.Id' --output text)
echo "  생성: $INVALIDATION_ID — 완료까지 대기 중"
aws cloudfront wait invalidation-completed \
  --distribution-id "$DISTRIBUTION_ID" \
  --id "$INVALIDATION_ID"
echo "  완료"

# ── 7. 검증 ─────────────────────────────────────────────────────────
echo ""
echo "▶ 검증"

ENTRY_JS=$(grep -o 'assets/index-[^"]*\.js' dist/index.html | head -1)
# 한글 청크(경매방-*.js). 백엔드 springdoc @Tag(한글) → orval 폴더명 →
# 청크 파일명 → S3 키로 이어진다. 이 경로가 깨지면 경매방·공유링크 화면
# 3개가 통째로 안 열린다. 로컬 preview 로는 검증할 수 없다.
KO_CHUNK=$(ls dist/assets | LC_ALL=C grep '[^ -~]' | head -1 || true)

check() { # $1=설명 $2=URL $3=기대 상태코드
  local code
  code=$(curl -sS -o /dev/null -w '%{http_code}' --path-as-is "$2" 2>/dev/null || echo "ERR")
  if [ "$code" = "$3" ]; then
    printf '  ✓ %-32s %s\n' "$1" "$code"
  else
    printf '  ✗ %-32s %s (기대: %s)\n' "$1" "$code" "$3"
  fi
}

check "진입점" "$FE_URL/" 200
check "해시 자산" "$FE_URL/$ENTRY_JS" 200
check "SPA 딥링크 /rooms/1" "$FE_URL/rooms/1" 200
check "SPA 딥링크 /join/abc123" "$FE_URL/join/abc123" 200
check "HTTP→HTTPS 리다이렉트" "http://www.upbid.store/" 301

# 연출 APNG 는 상태 코드만으로 검증할 수 없다. CloudFront 가 없는 파일에
# index.html 을 200 으로 돌려주기 때문에, 진짜 이미지인지 Content-Type 까지 본다.
check_image() { # $1=설명 $2=URL
  local ctype
  ctype=$(curl -sS -o /dev/null -w '%{content_type}' "$2" 2>/dev/null || echo "ERR")
  if [ "$ctype" = "image/png" ]; then
    printf '  ✓ %-32s %s\n' "$1" "$ctype"
  else
    printf '  ✗ %-32s %s (기대: image/png)\n' "$1" "$ctype"
  fi
}

for name in auction-cat-sold soft-close-extended auction-start; do
  check_image "연출 $name" "$FE_URL/motion/$MOTION_VERSION/$name.png"
done

if [ -n "$KO_CHUNK" ]; then
  ENCODED=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$KO_CHUNK")
  check "한글 청크 ($KO_CHUNK)" "$FE_URL/assets/$ENCODED" 200
fi

echo ""
echo "캐시 헤더 확인:"
curl -sSI "$FE_URL/" | grep -iE '^(cache-control|x-cache)' | sed 's/^/  index.html  /'
curl -sSI "$FE_URL/$ENTRY_JS" | grep -iE '^(cache-control|x-cache)' | sed 's/^/  asset       /'

echo ""
echo "✓ 배포 완료 — $FE_URL"
echo ""
echo "남은 것: 백엔드 FRONTEND_URL=$FE_URL 이 설정돼야 로그인이 된다."
echo "        (미설정이면 화면은 떠도 CORS 로 모든 API 가 막힌다)"
