#!/usr/bin/env python3
"""시연용 경매방의 알림 피드를 미리 채운다 (이슈 #333).

방에 들어가면 프론트가 GET /auction-rooms/share/{shareCode}/events 로 최근 이벤트를
불러와 피드에 그린다(rooms.$shareCode.index.tsx). 그 이벤트는 Redis 의 방별 ZSET 에
쌓이는데, 입찰 API 를 타야 쌓이므로 SQL 로 심은 입찰은 피드에 아무것도 남기지 않는다.

이 스크립트가 seed-demo-data.sql 이 넣은 물품과 입찰을 읽어 같은 형식의 이벤트를
직접 만들어 넣는다. 서버 코드는 건드리지 않는다.

쓰는 법:

    export MYSQL_PWD='...'
    MYSQL_CMD='mysql -h HOST -u USER --default-character-set=utf8mb4 upbid' \\
    REDIS_CMD='redis-cli -h HOST --pipe' \\
    python3 backend/demo/seed-events.py

운영 Redis 가 TLS 면 REDIS_CMD 에 --tls 를, 비밀번호가 있으면 -a 를 함께 넘긴다.
--dry-run 을 주면 Redis 에 쓰지 않고 무엇을 넣을지만 출력한다.
"""

import json
import os
import shlex
import subprocess
import sys
from datetime import datetime, timedelta

SHARE_CODES = ("x2RiepPnNioPZliA", "9pgvjVqW9KdBLd7x", "Wg10FGdlRdY2Vu16")

# 발행 스크립트가 키마다 다시 걸어주는 값과 같게 맞춘다 (SseEventPublisher.KEY_TTL).
KEY_TTL_SECONDS = 2 * 24 * 60 * 60

# 앱이 KST 벽시계로 LocalDateTime 을 쓰므로 DB 세션도 같은 기준으로 읽는다.
# occurredAt 만 Instant(UTC) 라 이 값을 빼서 만든다.
KST_OFFSET = timedelta(hours=9)

QUERY = """
SET time_zone = '+09:00';
SELECT kind, room_id, item_id, item_name, end_at, nickname, amount, occurred_at FROM (
    SELECT 'ITEM_STARTED' AS kind,
           a.auction_room_id AS room_id,
           ai.auction_item_id AS item_id,
           p.name AS item_name,
           ai.end_at AS end_at,
           '' AS nickname,
           0 AS amount,
           ai.started_at AS occurred_at
    FROM auction_rooms a
    JOIN auction_items ai ON ai.auction_room_id = a.auction_room_id
    JOIN products p ON p.product_id = ai.product_id
    WHERE a.share_code IN ({codes}) AND ai.status = 'IN_PROGRESS'
    UNION ALL
    SELECT 'BID_PLACED',
           a.auction_room_id,
           ai.auction_item_id,
           p.name,
           ai.end_at,
           u.nickname,
           b.amount,
           b.accepted_at
    FROM bids b
    JOIN auction_items ai ON ai.auction_item_id = b.auction_item_id
    JOIN auction_rooms a ON a.auction_room_id = ai.auction_room_id
    JOIN products p ON p.product_id = ai.product_id
    JOIN users u ON u.user_id = b.bidder_user_id
    WHERE a.share_code IN ({codes})
) x
ORDER BY room_id, occurred_at, item_id
"""


def read_rows(mysql_cmd):
    codes = ", ".join(f"'{code}'" for code in SHARE_CODES)
    query = QUERY.format(codes=codes)

    # 줄바꿈을 없애고 한 줄로 넘긴다. 여러 줄을 그대로 넘기면 셸을 거치면서 이스케이프가
    # 어긋나 MySQL 이 문장을 잘못 끊는다.
    result = subprocess.run(
        f"{mysql_cmd} -N -B -e {shlex.quote(' '.join(query.split()))}",
        shell=True, capture_output=True, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"MySQL 조회 실패:\n{result.stderr}")

    rows = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        kind, room_id, item_id, item_name, end_at, nickname, amount, occurred_at = line.split("\t")
        rows.append({
            "kind": kind,
            "room_id": int(room_id),
            "item_id": int(item_id),
            "item_name": item_name,
            "end_at": end_at,
            "nickname": nickname,
            "amount": int(amount),
            "occurred_at": occurred_at,
        })
    return rows


def to_instant(kst_wall_clock):
    """KST 벽시계 문자열을 앱이 occurredAt 에 쓰는 UTC Instant 표기로 바꾼다."""
    parsed = datetime.fromisoformat(kst_wall_clock) - KST_OFFSET
    return parsed.strftime("%Y-%m-%dT%H:%M:%S.%f") + "Z"


def build_envelope(event_id, row):
    if row["kind"] == "ITEM_STARTED":
        data = {
            "itemId": row["item_id"],
            "itemName": row["item_name"],
            # MySQL 은 "2026-08-17 22:56:59" 처럼 주는데 실제 이벤트는 Jackson 이 만든
            # ISO 표기라 가운데가 T 다. 그대로 넣으면 화면이 시각을 못 읽는다.
            "endedTime": row["end_at"].replace(" ", "T"),
        }
    else:
        data = {
            "itemId": row["item_id"],
            "itemName": row["item_name"],
            "bidPrice": row["amount"],
            "bidderNickname": row["nickname"],
        }

    message = {
        "roomId": row["room_id"],
        "eventName": row["kind"],
        "data": data,
        "occurredAt": to_instant(row["occurred_at"]),
    }
    # 저장 형식은 "{id}\n{JSON}" 이고 ZSET score 가 그 id 다 (SseEventEnvelope).
    return f"{event_id}\n" + json.dumps(message, ensure_ascii=False, separators=(",", ":"))


def resp(*args):
    out = [f"*{len(args)}\r\n"]
    for arg in args:
        encoded = str(arg).encode("utf-8")
        out.append(f"${len(encoded)}\r\n")
        out.append(encoded.decode("utf-8"))
        out.append("\r\n")
    return "".join(out)


def main():
    dry_run = "--dry-run" in sys.argv
    mysql_cmd = os.environ.get("MYSQL_CMD")
    redis_cmd = os.environ.get("REDIS_CMD")

    if not mysql_cmd:
        sys.exit("MYSQL_CMD 가 필요하다. 파일 맨 위 설명을 본다.")
    if not redis_cmd and not dry_run:
        sys.exit("REDIS_CMD 가 필요하다. 먼저 보려면 --dry-run 을 준다.")

    rows = read_rows(mysql_cmd)
    if not rows:
        sys.exit("넣을 이벤트가 없다. seed-demo-data.sql 을 먼저 돌렸는지 본다.")

    commands = []
    counts = {}

    for room_id in sorted({row["room_id"] for row in rows}):
        events_key = f"upbid:sse:{{room:{room_id}}}:events"
        sequence_key = f"upbid:sse:{{room:{room_id}}}:seq"

        # 두 번 돌렸을 때 같은 내용이 두 벌 쌓이지 않게 먼저 비운다. 실제 이벤트가 이미
        # 쌓인 방에는 돌리지 않는다 -- 그것까지 지워버린다.
        commands.append(resp("DEL", events_key, sequence_key))

        event_id = 0
        for row in [r for r in rows if r["room_id"] == room_id]:
            event_id += 1
            commands.append(resp("ZADD", events_key, event_id, build_envelope(event_id, row)))

        # 다음 실제 이벤트가 이어지는 번호를 받도록 카운터를 맞춰 둔다.
        commands.append(resp("SET", sequence_key, event_id))
        commands.append(resp("EXPIRE", events_key, KEY_TTL_SECONDS))
        commands.append(resp("EXPIRE", sequence_key, KEY_TTL_SECONDS))
        counts[room_id] = event_id

    if dry_run:
        for room_id, count in counts.items():
            print(f"방 {room_id}: 이벤트 {count}건")
        print()
        for row in rows[:5]:
            print(build_envelope(0, row).split("\n")[1])
        return

    result = subprocess.run(
        redis_cmd, shell=True, input="".join(commands), capture_output=True, text=True
    )
    print(result.stdout.strip() or result.stderr.strip())

    for room_id, count in counts.items():
        print(f"방 {room_id}: 이벤트 {count}건")


if __name__ == "__main__":
    main()
