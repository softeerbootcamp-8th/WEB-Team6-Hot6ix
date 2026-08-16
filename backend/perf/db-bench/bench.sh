#!/usr/bin/env bash
#
# 마감 예약 재동기화 쿼리를 인덱스 전후로 잰다. (이슈 #335)
#
#   ./bench.sh                                  # 1만, 10만, 100만 행을 차례로
#   ./bench.sh --rows 100000                    # 한 크기만
#   ./bench.sh --rows 10000 --reads 300         # 읽기 횟수를 늘려서
#
# 한 크기마다 이 순서로 돈다. 인덱스를 만들기 전과 후 사이에 표의 내용이 바뀌지 않으므로
# 두 라운드는 같은 데이터를 본다.
#
#   빈 MySQL → 스키마(V1, V2) → 시딩 → 읽기 → 쓰기 → 인덱스 생성 → 읽기 → 쓰기
#
# 결과는 results/ 아래에 로그와 TSV 로 남는다.

set -euo pipefail

cd "$(dirname "$0")"

COMPOSE="docker compose -f docker-compose.db-bench.yml"
MYSQL="$COMPOSE exec -T -e MYSQL_PWD=1234 mysql mysql -uroot --database=upbid"

ROWS="10000,100000,1000000"
READS=100
WRITES=200
IN_PROGRESS=30
READY=200
WARMUP=20
# 한 문장이 작으면 인덱스 갱신 비용이 문장 처리 비용에 묻힌다. 2,000 행으로 쟀을 때
# 인덱스 있는 쪽이 오히려 빨라 보일 만큼 흔들렸다. 표가 작으면 구간이 모자라 자동으로 줄어든다.
BULK_EACH=20000
BULK_REPEAT=5
KEEP=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --rows)        ROWS="$2"; shift 2 ;;
        --reads)       READS="$2"; shift 2 ;;
        --writes)      WRITES="$2"; shift 2 ;;
        --in-progress) IN_PROGRESS="$2"; shift 2 ;;
        --keep)        KEEP=1; shift ;;
        -h|--help)     sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "모르는 옵션: $1" >&2; exit 1 ;;
    esac
done

STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p results
LOG="results/$STAMP.log"
TSV="results/$STAMP.tsv"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 재는 쿼리다. AuctionItemRepository.findScheduleTargets 가 만드는 SQL 을 그대로 옮겼다.
# __TAG__ 자리에 라운드 이름이 들어가고, performance_schema 가 그 주석까지 문장 그대로
# 저장하기 때문에 라운드별로 실행 기록을 갈라낼 수 있다.
read -r -d '' QUERY <<'SQL' || true
select /*bench:__TAG__*/ ai1_0.auction_item_id, ai1_0.end_at, ai1_0.started_at, ar1_0.soft_close_trigger_seconds, ai1_0.notified_at from auction_items ai1_0 join auction_rooms ar1_0 on ar1_0.auction_room_id=ai1_0.auction_room_id where ai1_0.status='IN_PROGRESS' and ai1_0.end_at is not null order by ai1_0.end_at;
SQL

log() { echo "$@" | tee -a "$LOG"; }

mysql_file() { $MYSQL < "$1"; }

mysql_run() { echo "$1" | $MYSQL; }

# 한 줄짜리 TSV 로 받는다. 표 테두리 없이 값만 나온다.
mysql_batch() { $MYSQL --batch --skip-column-names < "$1"; }

# mysqladmin ping 으로는 부족하다. 데이터 디렉터리를 만드는 동안에도 임시 서버가 떠 있어서
# ping 은 통과하는데 root 접속은 거절당한다. 실제로 두 번째 라운드가 여기서 죽었다.
wait_healthy() {
    local i
    for i in $(seq 1 90); do
        if $MYSQL -e "select 1" >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    echo "MySQL 이 안 떴다" >&2
    exit 1
}

reset_db() {
    log "== 빈 MySQL 로 다시 시작 =="
    # 볼륨이 없어서 down 만으로 데이터가 사라진다.
    $COMPOSE down >/dev/null 2>&1 || true
    $COMPOSE up -d >/dev/null
    wait_healthy
    # 운영과 같은 스키마여야 인덱스 유무 말고 다른 것이 끼어들지 않는다.
    mysql_file ../../src/main/resources/db/migration/V1__baseline_schema.sql
    mysql_file ../../src/main/resources/db/migration/V2__add_notified_at_and_deal_candidate_rank_index.sql
}

seed() {
    local items=$1
    local rooms=$(( items / 20 ))
    local products=1000
    [[ $rooms -lt 10 ]] && rooms=10

    log "== 시딩: 물품 ${items} 건 (경매방 ${rooms}, 진행중 ${IN_PROGRESS}, 시작전 ${READY}) =="

    {
        echo "SET @rooms = $rooms; SET @products = $products; SET @items = $items;"
        cat sql/seed_base.sql
    } > "$TMP/base.sql"
    mysql_file "$TMP/base.sql"

    local batch=200000
    local from=1
    while [[ $from -le $items ]]; do
        local to=$(( from + batch - 1 ))
        [[ $to -gt $items ]] && to=$items
        {
            echo "SET SESSION cte_max_recursion_depth = 10000000;"
            echo "SET @rooms = $rooms; SET @products = $products; SET @items = $items;"
            echo "SET @in_progress = $IN_PROGRESS; SET @ready = $READY; SET @now = NOW(6);"
            echo "SET @from = $from; SET @to = $to;"
            cat sql/seed_items.sql
        } > "$TMP/items.sql"
        mysql_file "$TMP/items.sql"
        log "   ${from} ~ ${to}"
        from=$(( to + 1 ))
    done

    # 통계가 낡으면 옵티마이저가 인덱스를 놓고도 안 쓸 수 있다. 두 라운드 다 최신으로 맞춘다.
    mysql_run "ANALYZE TABLE auction_items, auction_rooms;" > /dev/null
}

stats() {
    local tag=$1
    {
        echo "SET @tag = '$tag';"
        cat sql/stats.sql
    } > "$TMP/stats.sql"
    mysql_batch "$TMP/stats.sql"
}

read_bench() {
    local tag=$1

    # 워밍업 실행은 태그를 따로 달아 집계에서 빠진다. 버퍼 풀에 페이지가 올라온 뒤부터 재려는 것이다.
    : > "$TMP/warm.sql"
    for _ in $(seq 1 $WARMUP); do
        echo "${QUERY//__TAG__/warm-$tag}" >> "$TMP/warm.sql"
    done
    mysql_file "$TMP/warm.sql" > /dev/null

    : > "$TMP/read.sql"
    for _ in $(seq 1 $READS); do
        echo "${QUERY//__TAG__/$tag}" >> "$TMP/read.sql"
    done
    mysql_file "$TMP/read.sql" > /dev/null

    stats "$tag"
}

# 마감된 물품이 모여 있는 구간이다. 뒤쪽 @in_progress + @ready 개는 쓰기 대상에서 뺀다.
# 그것까지 건드리면 인덱스 있는 라운드의 읽기가 볼 진행중 물품이 사라져서 두 라운드가
# 다른 데이터를 재게 된다.
closed_end() { echo $(( $1 - IN_PROGRESS - READY - 1 )); }

# SOLD 와 FAILED 를 서로 뒤집는다. 같은 값으로 덮으면 InnoDB 가 아무 일도 안 해서
# 인덱스 갱신 비용이 0 으로 나온다. 실제로 스모크 테스트에서 두 번째 라운드가 그렇게 나왔다.
FLIP="status = if(status='SOLD','FAILED','SOLD')"

write_bench() {
    local tag=$1
    local items=$2
    local half=$3   # 마감된 구간을 둘로 갈라 라운드마다 하나씩 쓴다. 같은 행을 두 번 쓰면
                    # 두 번째 라운드는 그 페이지가 이미 버퍼 풀에 있어서 유리해진다

    local last; last=$(closed_end "$items")
    local span=$(( last / 2 ))
    local base=1
    [[ $half -eq 2 ]] && base=$(( span + 1 ))

    local warm_from=$base
    local warm_to=$(( base + 49 ))
    : > "$TMP/wwarm.sql"
    local id
    for id in $(seq $warm_from $warm_to); do
        echo "update auction_items set $FLIP where auction_item_id=$id;" >> "$TMP/wwarm.sql"
    done
    mysql_file "$TMP/wwarm.sql" > /dev/null

    # 물품이 마감되면 status 가 바뀌고, 인덱스가 있으면 그 갱신이 따라 붙는다. 실제 전이는
    # IN_PROGRESS → SOLD 인데 그러면 진행중 건수가 줄어 읽기 라운드와 조건이 달라지므로,
    # 인덱스 갱신 비용이 같은 SOLD ↔ FAILED 로 잰다.
    : > "$TMP/write.sql"
    local from=$(( warm_to + 1 ))
    local to=$(( from + WRITES - 1 ))
    for id in $(seq $from $to); do
        echo "update /*bench:$tag*/ auction_items set $FLIP where auction_item_id=$id;" >> "$TMP/write.sql"
    done
    mysql_file "$TMP/write.sql" > /dev/null

    stats "$tag"

    # 같은 단건 UPDATE 를 커밋 fsync 없이 한 번 더 잰다. 실제 마감 경로는 위쪽(기본값 1)
    # 이지만, 커밋이 시간의 대부분이라 인덱스 갱신이 그 아래에 묻힌다. 여기서 갈리는지를
    # 봐야 "인덱스 때문에 쓰기가 얼마나 비싸졌나" 에 답할 수 있다.
    local nf_from=$(( to + 1 ))
    local nf_to=$(( nf_from + WRITES - 1 ))
    : > "$TMP/nofsync.sql"
    echo "SET GLOBAL innodb_flush_log_at_trx_commit = 2;" >> "$TMP/nofsync.sql"
    for id in $(seq $nf_from $nf_to); do
        echo "update /*bench:$tag-nofsync*/ auction_items set $FLIP where auction_item_id=$id;" >> "$TMP/nofsync.sql"
    done
    echo "SET GLOBAL innodb_flush_log_at_trx_commit = 1;" >> "$TMP/nofsync.sql"
    mysql_file "$TMP/nofsync.sql" > /dev/null

    stats "$tag-nofsync"

    # 한 문장으로 여러 행을 바꾸면 커밋이 한 번이라 인덱스 갱신 쪽이 크게 보인다. 표본이
    # 하나면 노트북 사정에 통째로 흔들리므로 구간을 나눠 여러 번 잰다.
    local room=$(( base + span - 1 - nf_to ))
    local each=$(( room / BULK_REPEAT ))
    [[ $each -gt $BULK_EACH ]] && each=$BULK_EACH
    : > "$TMP/bulk.sql"
    local b
    for b in $(seq 1 $BULK_REPEAT); do
        local bf=$(( nf_to + 1 + (b - 1) * each ))
        local bt=$(( bf + each - 1 ))
        echo "update /*bench:$tag-bulk*/ auction_items set $FLIP where auction_item_id between $bf and $bt;" >> "$TMP/bulk.sql"
    done
    mysql_file "$TMP/bulk.sql" > /dev/null

    # 이 함수의 표준출력은 결과 표로 잡히므로 안내는 로그 파일로만 보낸다.
    echo "   (단건 $from ~ $to, fsync 없이 $nf_from ~ $nf_to, 대량 ${each}행 × ${BULK_REPEAT}회)" >> "$LOG"
    stats "$tag-bulk"
}

# 입찰이 Soft Close 로 마감 시각을 미루는 경로다(AuctionItem.extendIfClosingSoon).
# 새 인덱스의 두 번째 열이 end_at 이라 이 UPDATE 마다 인덱스 갱신이 붙는데, 그게 입찰이
# 물품 행 락을 쥔 채로 도는 구간이라 마감 예약 조회와 달리 사용자가 기다리는 시간이다.
#
# 진행중 물품은 몇 건뿐이라 같은 행을 돌아가며 계속 때린다. 실제 입찰도 그렇다.
extend_bench() {
    local tag=$1
    local items=$2

    local first=$(( items - IN_PROGRESS + 1 ))
    local n id

    : > "$TMP/ewarm.sql"
    for n in $(seq 1 50); do
        id=$(( first + (n % IN_PROGRESS) ))
        echo "update auction_items set end_at = end_at + interval 30 second, total_extension_seconds = total_extension_seconds + 30 where auction_item_id=$id;" >> "$TMP/ewarm.sql"
    done
    mysql_file "$TMP/ewarm.sql" > /dev/null

    : > "$TMP/extend.sql"
    for n in $(seq 1 $WRITES); do
        id=$(( first + (n % IN_PROGRESS) ))
        echo "update /*bench:$tag*/ auction_items set end_at = end_at + interval 30 second, total_extension_seconds = total_extension_seconds + 30 where auction_item_id=$id;" >> "$TMP/extend.sql"
    done
    mysql_file "$TMP/extend.sql" > /dev/null

    stats "$tag"
}

table_size() {
    mysql_batch <(echo "select table_rows, round(data_length/1024/1024,1), round(index_length/1024/1024,1) from information_schema.tables where table_schema='upbid' and table_name='auction_items';")
}

explain_plan() {
    local label=$1
    log "-- EXPLAIN ($label) --"
    {
        echo "explain format=tree ${QUERY//__TAG__/explain}"
        echo "explain analyze ${QUERY//__TAG__/explain}"
    } > "$TMP/explain.sql"
    # TREE 출력은 한 칸에 줄바꿈이 들어 있어서 배치 모드 클라이언트가 \n 으로 escape 한다.
    mysql_file "$TMP/explain.sql" | sed 's/\\n/\
/g' | tee -a "$LOG"
}

run_size() {
    local items=$1

    reset_db
    seed "$items"

    log ""
    log "######## 물품 ${items} 건 ########"
    log "표 크기 (rows / data MB / index MB): $(table_size)"

    explain_plan "인덱스 없음"

    log "-- 읽기 (인덱스 없음) --"
    local r_no; r_no="$(read_bench "noidx-$items-read")"
    log "$r_no"

    log "-- 쓰기 (인덱스 없음) --"
    local w_no; w_no="$(write_bench "noidx-$items-write" "$items" 1)"
    log "$w_no"

    log "-- 입찰 연장 (인덱스 없음) --"
    local e_no; e_no="$(extend_bench "noidx-$items-extend" "$items")"
    log "$e_no"

    log "-- 인덱스 생성 --"
    local t0 t1
    t0=$(date +%s)
    mysql_run "create index idx_auction_items_status_end_at on auction_items (status, end_at);" > /dev/null
    t1=$(date +%s)
    log "   걸린 시간: $(( t1 - t0 ))초"
    mysql_run "ANALYZE TABLE auction_items;" > /dev/null
    log "표 크기 (rows / data MB / index MB): $(table_size)"

    explain_plan "인덱스 있음"

    log "-- 읽기 (인덱스 있음) --"
    local r_idx; r_idx="$(read_bench "idx-$items-read")"
    log "$r_idx"

    log "-- 쓰기 (인덱스 있음) --"
    local w_idx; w_idx="$(write_bench "idx-$items-write" "$items" 2)"
    log "$w_idx"

    log "-- 입찰 연장 (인덱스 있음) --"
    local e_idx; e_idx="$(extend_bench "idx-$items-extend" "$items")"
    log "$e_idx"

    {
        echo "$r_no"; echo "$w_no"; echo "$e_no"
        echo "$r_idx"; echo "$w_idx"; echo "$e_idx"
    } >> "$TSV"
}

log "행 수: $ROWS / 읽기 $READS 회 / 쓰기 $WRITES 회 / 워밍업 $WARMUP 회"
printf 'tag\tn\tmin_ms\tp50_ms\tp95_ms\tp99_ms\tmax_ms\trows_examined\trows_sent\n' > "$TSV"

IFS=',' read -r -a SIZES <<< "$ROWS"
for size in "${SIZES[@]}"; do
    run_size "$size"
done

if [[ $KEEP -eq 0 ]]; then
    $COMPOSE down >/dev/null 2>&1 || true
fi

log ""
log "로그: $LOG"
log "표:   $TSV"
