# 배포 nginx 설정

`api.upbid.store` 앞에 있는 nginx 설정입니다. **배스천 서버(`10.0.0.24`)에 있고 app EC2(`10.0.1.88`)와
다른 박스입니다.** 지금까지 서버에서 손으로만 고쳐서 저장소에 없었고, 그래서 프록시 한계를
앱 한계로 읽을 위험이 있었습니다 (이슈 #266).

**여기 있는 파일은 서버에 자동으로 반영되지 않습니다.** CD가 배포하는 건 앱뿐입니다.
서버를 고칠 때 이 파일을 같이 고쳐서, 다음 사람이 현재 설정을 읽을 수 있게 합니다.

## 파일 대응

| 저장소 | 서버 |
|---|---|
| `nginx.conf` | `/etc/nginx/nginx.conf` |
| `conf.d/upbid-redirect.conf` | `/etc/nginx/conf.d/upbid-redirect.conf` |
| `sites-available/upbid` | `/etc/nginx/sites-available/upbid` (`sites-enabled/upbid` 이 심볼릭 링크) |
| `snippets/upbid-proxy.conf` | `/etc/nginx/snippets/upbid-proxy.conf` |

## certbot 이 건드리는 줄

`sites-available/upbid` 의 `# managed by Certbot` 주석이 붙은 줄은 **certbot 이 넣은 것**입니다.
인증서를 갱신하거나 도메인을 추가하면 certbot 이 이 파일을 다시 씁니다. 그래서 서버 파일과
저장소 파일이 시간이 지나면 어긋날 수 있습니다. 고치기 전에 서버 쪽을 한 번 확인합니다.

```bash
sudo nginx -T
```

## 반영하는 법

파일을 옮긴 뒤 **문법 검사를 먼저** 하고 reload 합니다. `restart` 가 아니라 `reload` 라야
열려 있는 연결이 안 끊깁니다. SSE 접속이 붙어 있으면 특히 중요합니다.

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 지금 값에서 눈여겨볼 것

- **`keepalive 128` 과 `worker_connections 4096` 은 2026-08-11 에 서버에서 손으로 넣은
  것입니다.** 그전에는 keepalive 가 아예 없어서 요청마다 앱으로 TCP 를 새로 열었고, 끊은
  포트가 60초 잠기므로 포트 범위(약 28,000개) ÷ 60 = **초당 약 470건에서 포트가 마르는
  상태**였습니다. 로컬에서 앱만 재면 2,900 req/s 가 나오는데 앞단이 6분의 1로 막고
  있었습니다. 같은 날 `proxy_http_version 1.1` 과 클라이언트 HTTP/2 도 함께 켰습니다.
  자세한 경위와 검증은 `backend/plans/242-부하-측정-환경/인프라-현황.md` 에 있습니다.
- **`keepalive_timeout` 을 15초로 둔 것은 톰캣 기본 keep-alive 가 20초이기 때문입니다.**
  nginx 쪽을 더 길게 잡으면 톰캣이 먼저 닫은 커넥션으로 요청을 보내 간헐적 502 가 납니다.
  **`perf/nginx.conf` 의 `keepalive_timeout 300s` 를 운영에 그대로 옮기면 안 됩니다.**
- SSE 응답 버퍼링은 앱이 `X-Accel-Buffering: no` 를 보내서 이미 꺼집니다
  (`SseController`). nginx 설정으로 따로 끌 필요가 없습니다.
- SSE 동시 접속 상한은 `worker_processes(2) × worker_connections(4096) ÷ 2` 로 약 4,096 개
  입니다. SSE 하나가 슬롯을 둘(클라이언트 쪽, 앱으로 가는 쪽) 씁니다.
- **`access_log` 는 켜 둔 채입니다.** 부하를 걸면 초당 수천 줄이 디스크로 가서 재는 대상에
  로그 I/O 가 섞이는데, 끄면 운영 기록도 같이 없어집니다. 측정 창구에만 `access_log off;`
  를 넣었다가 되돌립니다.

## 측정 준비로 바꾼 것 (#266)

- **SSE 경로에만 `proxy_read_timeout` 을 1시간으로 올렸습니다.** 기본값 60초라, heartbeat
  주기를 60초 넘게 늘리는 대조 실험을 하면 서버가 아니라 nginx 가 끊어서 그 실험 자체를
  못 합니다. 나머지 경로는 기본값 그대로 둡니다. 다 늘리면 앱이 멈춘 요청까지 오래 붙듭니다.
- **`location /actuator` 를 `deny all` 로 막았습니다.** `/actuator` 에는 인증이 없어서
  (`AuthInterceptor` 가 `/api/v1` 아래만 봅니다) 노출을 켜면 힙과 DB 풀, 엔드포인트 목록이
  그대로 공개됩니다. **측정 서버는 이 프록시가 아니라 app EC2 사설 IP 를 직접 긁으므로
  여기에 `allow` 를 넣을 일이 없습니다.** 사설 IP 쪽은 보안 그룹으로 막습니다.
- 공통 프록시 헤더를 `snippets/upbid-proxy.conf` 로 뺐습니다. location 이 둘로 갈리면서
  한쪽에만 헤더가 빠지는 일을 막으려는 것입니다.

## 측정 창구에만 넣었다가 빼는 것

아래 둘은 **커밋해 두지 않습니다.** 측정하는 동안만 서버에서 넣고 끝나면 되돌립니다.
평소에 들어가 있으면 안 되는 값이라 파일에 남겨 두면 언젠가 그대로 배포됩니다.

**1. `dev-login` 을 측정 서버에서만 부를 수 있게 합니다.** 앱 쪽 토큰 게이트가 1차이고
이게 2차입니다. `server` 블록 안에 넣습니다.

```nginx
    location = /api/v1/auth/dev-login {
        allow 3.35.0.0;          # ← 측정 EC2 의 공인 IP 로 바꿉니다
        deny all;
        include /etc/nginx/snippets/upbid-proxy.conf;
    }
```

**2. `access_log` 를 끕니다.** 부하를 걸면 초당 수천 줄이 디스크로 가서 재는 대상에 로그
I/O 가 섞입니다. `server` 블록 안에 `access_log off;` 를 넣습니다.

넣은 뒤에도 똑같이 검사하고 reload 합니다. 끝나면 두 블록을 지우고 다시 reload 합니다.
