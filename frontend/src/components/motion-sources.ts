/**
 * 움직이는 연출 APNG 의 주소.
 *
 * **이 세 파일은 저장소에 없다.** 합쳐서 11.2MB 인데, Git 은 바이너리를 diff
 * 하지 못해 커밋할 때마다 통째로 새로 쌓는다. 한 번 커밋하면 나중에 지워도
 * 히스토리에 영원히 남아서 clone 과 CI checkout 이 계속 느려진다.
 *
 * 그래서 프론트 배포 버킷(`s3://upbid-frontend/motion/v1/`)에 손으로 올려 두고
 * 여기서는 주소만 들고 있다. 같은 CloudFront 배포라 same-origin 이고 CORS
 * 설정이 필요 없다.
 *
 * **경로에 버전을 박는 이유.** 빌드가 만드는 파일이 아니라서 파일명에 해시가
 * 안 붙는다. `v1` 이 그 역할을 대신하기 때문에 `immutable` 로 1년 캐시를 걸어도
 * 안전하다. 이미지를 고치면 `v2/` 로 올리고 이 상수만 바꾼다. CloudFront
 * 무효화도 필요 없다.
 *
 * **로컬 개발.** 같은 경로(`public/motion/v1/`)에 파일을 두면 vite 가 그대로
 * 서빙한다. 그 폴더는 `.gitignore` 대상이라 저장소에는 안 들어간다. 덕분에
 * 개발과 배포가 같은 경로를 쓰고 환경변수가 필요 없다.
 *
 * 파일이 없을 때를 대비해 각 컴포넌트가 번들에 남아 있는 정지 이미지로
 * 갈아탄다(`onError`). 그 정지 이미지들은 크기가 작아 저장소에 그대로 둔다.
 */
const MOTION_BASE = '/motion/v1'

export const MOTION_SOURCE = {
  auctionSold: `${MOTION_BASE}/auction-cat-sold.png`,
  softCloseExtended: `${MOTION_BASE}/soft-close-extended.png`,
  auctionStart: `${MOTION_BASE}/auction-start.png`,
} as const
