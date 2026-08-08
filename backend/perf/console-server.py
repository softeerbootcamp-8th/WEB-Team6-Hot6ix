#!/usr/bin/env python3
"""개발 콘솔이 부하 테스트를 눌러서 돌릴 수 있게 하는 작은 서버.

    python3 perf/console-server.py        # 18099 포트

브라우저는 셸을 못 돌린다. 그래서 콘솔 화면(:18000/dev-console.html)이 여기로 요청을 보내면
이 서버가 run.sh 를 실행하고, 진행 상황과 결과를 돌려준다.

왜 스프링 앱에 안 넣었나
    ProcessBuilder 로 셸을 돌리는 코드가 운영 jar 에 들어가는 게 부담스러워서다.
    프로파일로 막아도 코드 자체는 실려 나간다. 여기 두면 저장소에 파일 하나로 남고
    필요할 때만 띄운다.

표준 라이브러리만 쓴다. 설치할 게 없어야 팀원이 그냥 돌릴 수 있다.
"""

import http.cookiejar
import json
import os
import re
import shlex
import signal
import subprocess
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlparse

PERF_DIR = Path(__file__).resolve().parent
BACKEND_DIR = PERF_DIR.parent
RESULTS_DIR = PERF_DIR / "results"

PORT = int(os.environ.get("PERF_CONSOLE_PORT", "18099"))

# 콘솔 화면은 스프링 앱(:18000)이 서빙하므로 출처가 다르다. 개발용이라 로컬만 허용한다.
ALLOWED_ORIGIN_PATTERN = re.compile(r"^http://(localhost|127\.0\.0\.1):\d+$")

# run.sh 가 찍는 단계 표시. "[3/8] 기동 대기" 에서 3 과 문구를 뽑는다.
STEP_PATTERN = re.compile(r"^\[(\d+)/(\d+)\]\s*(.*)$")

MAX_LINES = 4000


class Run:
    """지금 돌고 있거나 마지막으로 끝난 실행 하나."""

    def __init__(self):
        self.lock = threading.Lock()
        self.process = None
        self.lines = []
        self.step = ""
        self.step_no = 0
        self.step_total = 8
        self.started_at = None
        self.finished_at = None
        self.exit_code = None
        self.command = ""
        self.run_id = None

    def is_running(self):
        return self.process is not None and self.process.poll() is None

    def snapshot(self, since=0):
        with self.lock:
            return {
                "running": self.is_running(),
                "command": self.command,
                "step": self.step,
                "stepNo": self.step_no,
                "stepTotal": self.step_total,
                "startedAt": self.started_at,
                "finishedAt": self.finished_at,
                "exitCode": self.exit_code,
                "runId": self.run_id,
                "elapsed": int(time.time() - self.started_at) if self.started_at and not self.finished_at
                           else (int(self.finished_at - self.started_at) if self.started_at and self.finished_at else 0),
                "totalLines": len(self.lines),
                "lines": self.lines[since:],
            }

    def append(self, text):
        with self.lock:
            self.lines.append(text)
            if len(self.lines) > MAX_LINES:
                # 앞부분을 버린다. 계속 쌓으면 브라우저로 보내는 응답이 무거워진다.
                del self.lines[: len(self.lines) - MAX_LINES]

            matched = STEP_PATTERN.match(text)
            if matched:
                self.step_no = int(matched.group(1))
                self.step_total = int(matched.group(2))
                self.step = matched.group(3)

            # run.sh 가 맨 앞에 "═══ 실행이름 ═══" 을 찍는다. 결과 폴더를 찾는 데 쓴다.
            if text.startswith("═══") and "_s" in text:
                self.run_id = text.strip("═ ").strip()


current = Run()


def build_command(body):
    """요청 본문을 run.sh 인자로 바꾼다.

    문자열을 그대로 셸에 넘기지 않는다. 숫자는 정수로 강제하고 이름은 흰 목록 문자만 남긴다.
    이렇게 해야 콘솔 화면이 뭘 보내든 실행되는 명령의 모양이 정해진다.
    """
    def num(key, default, low, high):
        try:
            value = int(body.get(key, default))
        except (TypeError, ValueError):
            value = default
        return max(low, min(high, value))

    # run.sh 의 case 문이 0~4 만 받는다. 5 를 보내면 컨테이너 기동과 시딩을 다 하고 나서
    # "모르는 시나리오" 로 죽는다. 여기서 먼저 막는다.
    scenario = num("scenario", 1, 0, 4)
    args = ["./perf/run.sh", "--scenario", str(scenario)]

    if scenario != 4:
        args += ["--vus", str(num("vus", 40, 1, 5000))]

    # 물품 수는 기본값이어도 붙인다. 시나리오 1과 2를 가르는 값이라 명령에 보여야 한다.
    args += ["--items", str(num("items", 1, 1, 50))]

    # 나머지는 기본값이면 안 붙인다. 화면이 보여주는 "실행될 명령"과 실제로 도는 명령이
    # 같아야 하는데, 안 바꾼 값까지 붙으면 무엇을 바꿨는지가 명령에서 안 보인다.
    pool = num("pool", 10, 1, 200)
    if pool != 10:
        args += ["--pool", str(pool)]

    sse = num("sse", 0, 0, 5000)
    if sse:
        args += ["--sse", str(sse)]

    users = num("users", 200, 1, 2000)
    if users != 200:
        args += ["--users", str(users)]

    duration = num("durationSeconds", 180, 10, 3600)
    if duration != 180:
        args += ["--duration", f"{duration}s"]

    # 아래 셋은 기본값이면 안 붙인다. 화면이 만들어 보여주는 명령과 실제로 도는 명령이
    # 같아야 하는데, 안 바꾼 값까지 붙으면 "내가 뭘 바꿨더라"가 명령에서 안 보인다.
    scheduler_pool = num("schedulerPool", 4, 1, 64)
    if scheduler_pool != 4:
        args += ["--scheduler-pool", str(scheduler_pool)]

    heartbeat_ms = num("heartbeatMs", 30000, 1000, 600000)
    if heartbeat_ms != 30000:
        args += ["--heartbeat-ms", str(heartbeat_ms)]

    # run.sh 가 "-Xmx$2" 로 조립하므로 크기 표기만 남긴다 (예: 512m).
    xmx = re.sub(r"[^0-9kmgKMG]", "", str(body.get("xmx", ""))[:8])
    if xmx:
        args += ["--xmx", xmx]

    who = re.sub(r"[^0-9A-Za-z가-힣_-]", "", str(body.get("who", ""))[:20])
    if who:
        args += ["--who", who]

    if body.get("skipBuild"):
        args.append("--skip-build")

    return args


def start_run(body):
    if current.is_running():
        return None, "이미 측정이 돌고 있습니다"

    args = build_command(body)

    fresh = Run()
    fresh.command = " ".join(shlex.quote(a) for a in args)
    fresh.started_at = time.time()

    env = dict(os.environ)
    env.setdefault("PERF_HTTP_PORT", os.environ.get("PERF_HTTP_PORT", "18080"))
    env.setdefault("PERF_PROM_PORT", os.environ.get("PERF_PROM_PORT", "19090"))
    env.setdefault("PERF_GRAFANA_PORT", os.environ.get("PERF_GRAFANA_PORT", "13000"))

    fresh.process = subprocess.Popen(
        args,
        cwd=str(BACKEND_DIR),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        env=env,
        # run.sh 가 도커와 gradle 을 띄우므로 자식이 여럿이다. 정지시킬 때 한 번에 잡으려고
        # 프로세스 그룹을 따로 만든다.
        start_new_session=True,
    )

    globals()["current"] = fresh

    def pump():
        for line in fresh.process.stdout:
            fresh.append(line.rstrip("\n"))
        fresh.process.wait()
        fresh.exit_code = fresh.process.returncode
        fresh.finished_at = time.time()
        fresh.append("")
        fresh.append(f"── 끝났습니다 (종료 코드 {fresh.exit_code}) ──")

    threading.Thread(target=pump, daemon=True).start()
    return fresh, None


def stop_run():
    if not current.is_running():
        return False
    os.killpg(os.getpgid(current.process.pid), signal.SIGTERM)
    current.append("── 사용자가 정지시켰습니다 ──")
    return True


def read_results(limit=40):
    """index.csv 를 읽어 계단을 표로 돌려준다. 없으면 빈 목록."""
    index = RESULTS_DIR / "index.csv"
    if not index.exists():
        return {"header": [], "rows": []}

    lines = [l for l in index.read_text(encoding="utf-8").splitlines() if l.strip()]
    if not lines:
        return {"header": [], "rows": []}

    header = lines[0].split(",")
    rows = [l.split(",") for l in lines[1:]][-limit:]
    rows.reverse()          # 최근 실행이 위로
    return {"header": header, "rows": rows}


# ══════════════ 돌아가는 동안의 지표 ══════════════
# 브라우저가 Prometheus 를 직접 치지 않고 여기를 거친다. 이유가 둘이다.
#
#   1. 지금 실행의 라벨(run="...")을 콘솔 서버만 안다. run.sh 가 찍는 실행 이름을 로그에서
#      뽑아 두기 때문이다. 브라우저가 직접 조립하면 실행 이름 규칙이 두 군데로 갈린다.
#   2. PromQL 이 run.sh 와 화면으로 갈리면, 같은 실행인데 표의 숫자와 화면의 숫자가 달라
#      보인다. 그러면 어느 쪽을 믿어야 하는지 알 수 없다.
#
# 다만 여기 쿼리는 run.sh 의 "값 뽑기"와 목적이 다르다. run.sh 는 구간 전체를 한 번에
# 계산하고(5초마다 찍힌 p95 를 평균 내는 건 틀리므로 그게 옳다), 여기는 "지금 이 순간"을
# 보여주는 것이라 최근 30초만 본다. 그래서 이 값과 최종 결과가 조금 다른 게 정상이다.

PROM_URL = "http://localhost:" + os.environ.get("PERF_PROM_PORT", "19090")

# run.sh 와 같은 제외 조건이다. 액추에이터 자신과 SSE 구독을 빼지 않으면, 끝나지 않는
# 스트림이 끊길 때 수십 초짜리 응답 하나로 기록돼서 p95 가 통째로 그쪽으로 끌려간다.
NOT_ACTUATOR = 'uri!="/actuator/prometheus", uri!~".*subscribe"'

LIVE_QUERIES = {
    "throughput": 'sum(rate(http_server_requests_seconds_count{{{run}, {skip}}}[30s]))',
    "p95Ms": 'histogram_quantile(0.95, sum by (le) '
             '(rate(http_server_requests_seconds_bucket{{{run}, {skip}}}[30s]))) * 1000',
    "tomcatBusy": 'max(tomcat_threads_busy_threads{{{run}}})',
    "hikariPending": 'max(hikaricp_connections_pending{{{run}}})',
    "hikariActive": 'max(hikaricp_connections_active{{{run}}})',
    "sseConnections": 'max(upbid_sse_connections{{{run}}})',
    "lockWaitP95Ms": 'histogram_quantile(0.95, sum by (le) '
                     '(rate(upbid_bid_lock_wait_seconds_bucket{{{run}}}[30s]))) * 1000',
}


def _prom(query):
    """Prometheus 에 즉시 쿼리 하나. 못 읽으면 None 을 준다.

    None 과 0 을 반드시 가른다. 화면이 "아직 안 나온 값"과 "0이었던 값"을 같게 그리면,
    커넥션 대기가 0이라 정상인 상태와 Prometheus 가 아직 안 긁은 상태가 구분되지 않는다.
    """
    url = PROM_URL + "/api/v1/query?" + urlencode({"query": query})

    try:
        with urllib.request.urlopen(url, timeout=3) as response:
            payload = json.loads(response.read() or b"{}")
    except Exception:
        return None

    result = (payload.get("data") or {}).get("result") or []

    if not result:
        return None

    try:
        value = float(result[0]["value"][1])
    except (KeyError, IndexError, TypeError, ValueError):
        return None

    # Prometheus 는 NaN 을 문자열 "NaN" 으로 준다. float() 는 받아 주지만 JSON 으로는
    # 못 내보내고(json 이 NaN 을 뱉으면 JS 의 JSON.parse 가 죽는다) 뜻도 "값 없음"이다.
    return None if value != value else value


def read_live():
    """지금 돌고 있는 실행의 최근 30초 지표. 실행 중이 아니거나 이름을 모르면 빈 값."""
    run_id = current.run_id

    if not run_id:
        return {"runId": None, "metrics": {}}

    labels = {"run": 'run="%s"' % run_id, "skip": NOT_ACTUATOR}

    return {
        "runId": run_id,
        "metrics": {name: _prom(template.format(**labels))
                    for name, template in LIVE_QUERIES.items()},
    }


# ══════════════ 서버 로그 ══════════════
# 콘솔이 서버 로그를 직접 보여준다. 지금까지는 "앱이 무슨 말을 하는지" 보려면 런처를 띄운
# 터미널 창을 찾아 들어가야 했고, 측정용 앱은 docker compose logs 를 손으로 쳐야 했다.
# 무엇이 잘못됐는지는 대개 거기 적혀 있는데, 화면에서 세 걸음 떨어져 있으면 안 보게 된다.

COMPOSE_FILE = str(PERF_DIR / "docker-compose.perf.yml")

# 런처가 bootRun 출력을 여기로도 흘려 둔다 (UpBid 개발환경 켜기.command).
DEV_APP_LOG = Path(os.environ.get("UPBID_DEV_LOG", "/tmp/upbid-backend.log"))

# 통째로 읽지 않는다. 앱이 몇 시간 돌면 로그가 수십 MB 가 되는데 그걸 매번 읽어
# 브라우저로 보내면 콘솔이 앱보다 무거워진다.
TAIL_BYTES = 256 * 1024


def _tail_file(path, tail):
    size = path.stat().st_size

    with path.open("rb") as handle:
        handle.seek(max(0, size - TAIL_BYTES))
        data = handle.read()

    # 잘라 읽었으므로 첫 줄이 중간부터 시작할 수 있다. 그 줄은 버린다.
    lines = data.decode("utf-8", "replace").splitlines()

    if size > TAIL_BYTES and lines:
        lines = lines[1:]

    return lines[-tail:]


def read_server_log(which, tail):
    """개발 앱(파일) 또는 측정용 앱(도커)의 최근 로그."""
    if which == "dev":
        if not DEV_APP_LOG.exists():
            return {"lines": [], "note":
                    "%s 가 없습니다. 런처(UpBid 개발환경 켜기.command)로 앱을 띄우면 "
                    "여기에 로그가 쌓입니다. 이미 띄워 두셨다면 런처를 한 번 다시 켜야 합니다."
                    % DEV_APP_LOG}

        return {"lines": _tail_file(DEV_APP_LOG, tail), "note": ""}

    try:
        done = subprocess.run(
            ["docker", "compose", "-f", COMPOSE_FILE, "logs", "--no-color", "--tail", str(tail), "app"],
            cwd=str(BACKEND_DIR), capture_output=True, text=True, timeout=15)
    except Exception as e:
        return {"lines": [], "note": "도커 로그를 못 읽었습니다: %s" % e}

    lines = [l for l in (done.stdout or "").splitlines() if l.strip()]

    if not lines:
        return {"lines": [], "note":
                "측정용 앱이 안 떠 있습니다. 측정을 한 번 돌리면 그때 뜨고, 여기에 로그가 나옵니다."
                + (" (%s)" % done.stderr.strip() if done.stderr.strip() else "")}

    return {"lines": lines, "note":
            "perf 프로파일은 로그 수준이 warn 이라 평소에는 조용한 게 정상입니다. "
            "재는 대상이 서비스가 아니라 로그 출력이 되면 안 되기 때문입니다."}


# ══════════════ 동시 입찰 검증 ══════════════
# 브라우저로는 못 하는 것이다. 오리진당 세션 쿠키가 하나라 한 번에 한 사람만 될 수 있고,
# SESSION 이 httpOnly 라 JS 가 헤더로 붙일 수도 없다. 그래서 개발 콘솔의 "입찰 흘리기"는
# 계정을 갈아타며 **순차로** 흉내 내는 것이라 락 경합이 아예 안 생긴다.
#
# 여기서는 쿠키 항아리를 N개 따로 들고, 스레드 배리어로 **같은 순간에** 쏜다.
# 부하가 목적이 아니라 정합성 확인이 목적이라 N 이 작아도 된다.

APP_BASE = os.environ.get("PERF_APP_BASE", "http://localhost:18000") + "/api/v1"


def _client():
    """쿠키를 따로 들고 다니는 HTTP 클라이언트 하나. 사람 한 명에 하나씩 만든다."""
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def _post(opener, path, body=None, timeout=15):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(
        APP_BASE + path, data=data, method="POST",
        headers={"Content-Type": "application/json"} if data else {})
    try:
        with opener.open(request, timeout=timeout) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"{}")
        except json.JSONDecodeError:
            return e.code, {}
    except Exception as e:
        return 0, {"message": str(e)}


def _get(opener, path, timeout=15):
    try:
        with opener.open(APP_BASE + path, timeout=timeout) as response:
            return json.loads(response.read() or b"{}")
    except Exception:
        return {}


def concurrent_bid(body):
    """같은 물품에 N명이 동시에 입찰하고, 결과가 규칙에 맞는지 본다."""
    share_code = str(body.get("shareCode", ""))
    item_id = int(body.get("itemId", 0))
    people = max(2, min(50, int(body.get("bidders", 10))))
    same_amount = bool(body.get("sameAmount", True))
    unit = max(1, int(body.get("unit", 1000)))

    if not share_code or not item_id:
        return {"error": "shareCode 와 itemId 가 필요합니다"}

    probe = _client()
    before = _get(probe, "/auction-rooms/share/%s/auction-items" % share_code)
    items = [i for i in (before.get("data") or []) if i.get("auctionItemId") == item_id]

    if not items:
        return {"error": "물품 %d 을 이 방에서 못 찾았습니다" % item_id}

    item = items[0]
    if item.get("status") != "IN_PROGRESS":
        return {"error": "물품이 진행중이 아닙니다 (지금 %s)" % item.get("status")}

    has_leader = len(item.get("leaderboard") or []) > 0
    base = int(item["currentPrice"]) + (unit if has_leader else 0)

    # 사람마다 클라이언트를 따로 만들고 미리 로그인·동의까지 끝낸다.
    # 로그인이 발사 순간에 섞이면 "동시에 쐈다"가 아니게 된다.
    people_clients = []
    for n in range(1, people + 1):
        opener = _client()
        status, _ = _post(opener, "/auth/dev-login?" + urlencode({"key": "bidder-%d" % n}))
        if status != 200:
            return {"error": "bidder-%d 로그인 실패 (status %s)" % (n, status)}
        _post(opener, "/auction-rooms/share/%s/agreement" % share_code)
        people_clients.append(("bidder-%d" % n, opener))

    barrier = threading.Barrier(people)
    results = [None] * people

    def fire(index, name, opener, amount):
        barrier.wait()                      # 전원이 모일 때까지 기다렸다 한꺼번에 나간다
        started = time.perf_counter()
        status, payload = _post(opener, "/auction-items/%d/bids" % item_id, {"amount": amount})
        results[index] = {
            "who": name, "amount": amount, "status": status,
            "code": payload.get("code"), "message": payload.get("message"),
            "ms": round((time.perf_counter() - started) * 1000, 1),
        }

    threads = []
    for index, (name, opener) in enumerate(people_clients):
        amount = base if same_amount else base + unit * index
        thread = threading.Thread(target=fire, args=(index, name, opener, amount))
        threads.append(thread)
        thread.start()

    for thread in threads:
        thread.join(timeout=40)

    results = [r for r in results if r]
    accepted = [r for r in results if r["status"] == 201]

    after_all = _get(probe, "/auction-rooms/share/%s/auction-items" % share_code)
    after = next((i for i in (after_all.get("data") or [])
                  if i.get("auctionItemId") == item_id), {})

    outcomes = {}
    for r in results:
        if r["status"] == 201:
            key = "접수 (201)"
        elif r["code"]:
            key = "거절 %s" % r["code"]
        else:
            key = "HTTP %s" % r["status"]
        outcomes[key] = outcomes.get(key, 0) + 1

    leader = (after.get("leaderboard") or [{}])[0].get("nickname")
    final_price = after.get("currentPrice")
    highest = max((r["amount"] for r in accepted), default=None)

    # 이 넷 중 하나라도 깨지면 동시성 문제다.
    checks = [
        {"name": "5xx·타임아웃이 없다",
         "ok": all(r["status"] < 500 and r["status"] != 0 for r in results)},
        {"name": "같은 금액이 두 번 접수되지 않았다",
         "ok": len({r["amount"] for r in accepted}) == len(accepted)},
        {"name": "최종 현재가가 접수된 최고가와 같다",
         "ok": (highest is None) or (final_price == highest)},
        {"name": "최종 1위가 그 입찰의 주인이다",
         "ok": (highest is None) or any(r["who"] == leader and r["amount"] == highest
                                        for r in accepted)},
    ]

    return {
        "mode": "같은 금액으로 동시에" if same_amount else "서로 다른 금액으로 동시에",
        "bidders": people,
        "baseAmount": base,
        "outcomes": outcomes,
        "accepted": len(accepted),
        "finalPrice": final_price,
        "finalLeader": leader,
        "checks": checks,
        "allOk": all(c["ok"] for c in checks),
        "detail": sorted(results, key=lambda r: r["ms"]),
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass        # 요청 로그는 안 남긴다. 콘솔이 1초마다 폴링해서 화면이 지저분해진다.

    # ── 공통 ────────────────────────────────────────────────────────
    def _cors(self):
        origin = self.headers.get("Origin", "")
        if ALLOWED_ORIGIN_PATTERN.match(origin):
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

    def _json(self, payload, status=200):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.send_header("Content-Length", "0")
        self.end_headers()

    # ── 라우팅 ──────────────────────────────────────────────────────
    def do_GET(self):
        path = urlparse(self.path).path
        query = urlparse(self.path).query

        if path == "/health":
            return self._json({"ok": True, "port": PORT, "backendDir": str(BACKEND_DIR)})

        if path == "/status":
            since = 0
            for part in query.split("&"):
                if part.startswith("since="):
                    try:
                        since = int(part[6:])
                    except ValueError:
                        since = 0
            return self._json(current.snapshot(since))

        if path == "/results":
            return self._json(read_results())

        if path == "/live":
            return self._json(read_live())

        if path == "/serverlog":
            params = parse_qs(query)
            which = (params.get("which") or ["perf"])[0]

            try:
                tail = max(20, min(1000, int((params.get("tail") or ["300"])[0])))
            except ValueError:
                tail = 300

            return self._json(read_server_log("dev" if which == "dev" else "perf", tail))

        return self._json({"error": "not found"}, 404)

    def do_POST(self):
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"

        try:
            body = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            return self._json({"error": "본문이 JSON 이 아닙니다"}, 400)

        # 화면이 "실행될 명령"으로 보여줄 문자열. 실제로 도는 것과 같아야 하므로
        # 브라우저가 제 손으로 조립하지 않고 여기서 받아 간다. 조립 규칙이 두 군데로
        # 갈리면 화면에 적힌 명령과 실제로 도는 명령이 조용히 달라진다.
        #
        # 실행기가 꺼져 있을 때는 브라우저가 제 손으로 조립한 것을 보여준다. 그때는
        # 사람이 그 명령을 직접 터미널에 붙여 넣으므로 그게 곧 실제로 도는 명령이다.
        if path == "/preview":
            return self._json({
                "command": " ".join(shlex.quote(a) for a in build_command(body)),
            })

        if path == "/run":
            started, error = start_run(body)
            if error:
                return self._json({"error": error}, 409)
            return self._json({"started": True, "command": started.command}, 202)

        if path == "/concurrent-bid":
            outcome = concurrent_bid(body)
            return self._json(outcome, 400 if outcome.get("error") else 200)

        if path == "/stop":
            return self._json({"stopped": stop_run()})

        return self._json({"error": "not found"}, 404)


def main():
    RESULTS_DIR.mkdir(exist_ok=True)
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)

    print(f"부하 테스트 콘솔 서버가 http://localhost:{PORT} 에서 돕니다.")
    print(f"  실행 위치: {BACKEND_DIR}")
    print(f"  개발 콘솔: http://localhost:18000/dev-console.html 의 [부하 테스트] 탭")
    print("  Ctrl+C 로 끕니다.")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        if current.is_running():
            print("\n돌던 측정을 정지시킵니다...")
            stop_run()
        print("끝냅니다.")


if __name__ == "__main__":
    main()
