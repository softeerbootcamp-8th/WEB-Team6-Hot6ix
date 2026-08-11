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

- **`upstream app` 의 `keepalive 128` 은 이미 들어가 있습니다.** 요청마다 TCP 를 새로 여는
  문제는 없습니다.
- **`proxy_read_timeout` 이 없어서 기본값 60초입니다.** SSE emitter 타임아웃이 1시간인데
  nginx 가 60초에 끊습니다. 지금은 heartbeat 가 30초라 그 전에 데이터가 흘러 살아 있지만,
  **heartbeat 주기를 60초 넘게 늘리는 대조 실험을 하면 서버가 아니라 nginx 가 끊습니다.**
- SSE 응답 버퍼링은 앱이 `X-Accel-Buffering: no` 를 보내서 이미 꺼집니다
  (`SseController`). nginx 설정으로 따로 끌 필요가 없습니다.
- `access_log` 가 켜져 있어서 부하를 걸면 초당 수천 줄이 디스크로 갑니다. 측정 창구에만
  끄는 것을 검토합니다.
