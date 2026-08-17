# Redis AOF 운영 설정

진행 중인 경매에서는 Redis Hash와 Stream이 판정·복구의 기준이므로 RDB 스냅샷만으로는
충분하지 않습니다. 이 디렉터리는 운영 Redis를 다음 값으로 전환하고 확인하는 도구입니다.

```text
appendonly yes
appendfsync always
maxmemory-policy noeviction
dir /var/lib/redis
appenddirname appendonlydir
```

저장소에는 비밀번호, 인증서, 개인 키를 넣지 않습니다. 스크립트는 TLS CA를 기본적으로
`/etc/redis/tls/ca.crt`에서 읽고, 인증값은 `REDISCLI_AUTH` 또는 root만 읽을 수 있는 기존
`/etc/redis/redis.conf`의 활성 `requirepass`에서 가져옵니다. 인증값은 출력하거나
`redis-cli` 명령행 인자로 전달하지 않습니다.

## 실행 전 중단 조건

다음 중 하나라도 해당하면 활성화하지 않습니다.

- 팀원의 부하 테스트가 진행 중이다.
- 실제 경매가 진행 중이다.
- `/var/lib/redis`의 디스크 여유 공간과 EBS mount를 확인하지 못했다.
- 현재 `dump.rdb`와 `/etc/redis/redis.conf`를 백업할 수 없다.
- Redis AOF rewrite 또는 write 상태가 이미 실패 상태다.

설정 설치와 활성화는 Redis 쓰기 특성을 변경합니다. 특히 AOF 활성화 시 초기 rewrite가
발생하고 `appendfsync always` 이후에는 쓰기마다 fsync 비용이 포함되므로 부하 테스트 결과와
입찰 지연에 영향을 줍니다.

## 저장소 검증

```bash
bash backend/deploy/redis/test/redis-persistence-contract-test.sh
bash -n backend/deploy/redis/*.sh \
  backend/deploy/redis/lib/*.sh \
  backend/deploy/redis/test/*.sh
```

## 서버로 복사

저장소 루트에서 실행합니다.

```bash
scp -r backend/deploy/redis upbid-redis:/tmp/upbid-redis-aof-342
ssh upbid-redis
cd /tmp/upbid-redis-aof-342
```

## 재시작 전 적용 순서

먼저 Redis 서비스 상태, 디스크와 현재 persistence 상태를 읽기 전용으로 확인합니다. TLS와
인증이 필요하므로 평문 `redis-cli` 대신 이 디렉터리의 공통 함수를 사용하는 스크립트로
최종 상태를 확인합니다.

```bash
systemctl is-active redis-server
findmnt -T /var/lib/redis
df -hT /var/lib/redis
sudo systemctl show redis-server -p ActiveEnterTimestamp
```

아래 순서를 바꾸면 안 됩니다. 설정 파일에 `appendonly yes`를 먼저 설치한 상태에서 Redis가
우연히 재시작되는 일을 막기 위해, 백업 단계는 persistence include를 설치하지 않습니다.

```bash
# 1. 현재 redis.conf와 dump.rdb만 백업합니다.
sudo ./install-persistence-config.sh --backup-only

# 2. 현재 프로세스에서 always/noeviction을 설정하고 AOF를 켭니다.
#    초기 rewrite와 write 상태가 모두 ok가 될 때까지 기다립니다.
sudo UPBID_AOF_ACTIVATION_CONFIRM=activate-without-restart ./activate-aof.sh

# 3. rewrite 성공 후 다음 기동에도 같은 값을 사용하도록 include를 설치합니다.
sudo ./install-persistence-config.sh

# 4. 후속 재시작 검증용 Hash, Stream, Consumer Group과 PEL 한 건을 만듭니다.
sudo ./prepare-recovery-fixture.sh

# 5. 런타임 설정, AOF 파일과 fixture를 읽기 전용으로 검증합니다.
sudo ./verify-persistence.sh
```

활성화 스크립트는 다음 조건을 모두 만족해야 성공합니다.

```text
aof_enabled:1
aof_rewrite_in_progress:0
aof_rewrite_scheduled:0
aof_last_bgrewrite_status:ok
aof_last_write_status:ok
```

오류가 발생하면 스크립트는 non-zero로 종료합니다. 자동으로 `appendonly no`로 되돌리거나
Redis를 재시작하지 않습니다. 실패 상태와 로그를 보존하고 원인을 먼저 확인합니다.

## Fixture 보존

fixture는 `upbid:recovery:test:<run-id>:*` 네임스페이스만 사용합니다. 식별 정보는
`/var/tmp/upbid-redis-recovery-fixture.env`에 mode `0600`으로 저장됩니다. Stream 레코드는
Consumer Group이 읽은 뒤 ACK하지 않으므로 PEL에 한 건 남습니다.

이번 단계에서는 fixture를 삭제하거나 ACK하지 않습니다. 별도 유지보수 시간에 Redis 프로세스
재시작, 강제 종료, EC2 재부팅 후 Hash·Stream·PEL이 모두 복구되는지 확인하기 위한 기준입니다.

## 이번 단계에서 하지 않는 작업

- `systemctl restart redis-server`
- Redis 프로세스 강제 종료
- EC2 재부팅
- fixture `XACK`, `XDEL`, `DEL`
- Pending 이벤트의 MySQL 재전달과 `requestId` 멱등성 검증

위 항목은 이슈 #342의 후속 장애 복구 단계에서 별도 승인을 받은 뒤 수행합니다.
