/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  /** 'on' 이면 저장된 세션이 없을 때 목업 판매자로 시작한다 (시연용). */
  readonly VITE_DEMO_AUTOLOGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
