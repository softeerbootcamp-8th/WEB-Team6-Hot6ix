#!/usr/bin/env bash
# Finder 에서 더블클릭하면 부하 테스트 콘솔 서버가 뜬다.
#
# 터미널에 명령을 칠 필요가 없게 하려고 둔 파일이다. macOS 는 실행 권한이 있는 .command 를
# 더블클릭하면 터미널에서 실행해 준다. 창은 뜨지만 아무것도 안 쳐도 된다.
#
# 처음 한 번은 Finder 에서 우클릭 > 열기 로 실행해야 할 수 있다 (Gatekeeper).

cd "$(dirname "$0")/.." || exit 1

echo "════════════════════════════════════════════════"
echo "  UpBid 부하 테스트 콘솔"
echo "════════════════════════════════════════════════"
echo
echo "  이 창은 켜 둔 채로 두세요. 끄면 실행이 멈춥니다."
echo "  브라우저에서 아래를 엽니다."
echo
echo "    http://localhost:18000/dev-console.html"
echo
echo "  [부하 테스트] 탭에서 [이 조건으로 실행] 을 누르면 됩니다."
echo "════════════════════════════════════════════════"
echo

exec python3 perf/console-server.py
