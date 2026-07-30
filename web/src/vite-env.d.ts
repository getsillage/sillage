/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SILLAGE_VERSION?: string;
  readonly VITE_SILLAGE_REVISION?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
