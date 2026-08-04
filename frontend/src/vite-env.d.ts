/// <reference types="vite/client" />

interface ImportMetaEnv {
  // 로컬 `.env` 에는 없다. CI 가 배포 빌드에만 넣는다(CICD-FE.yml).
  readonly VITE_API_BASE_URL: string | undefined
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
