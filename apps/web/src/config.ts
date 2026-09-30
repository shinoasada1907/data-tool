// Phải khớp IMPORTER_MAX_FILE_SIZE của BE (mặc định 20MB).
const DEFAULT_MAX_UPLOAD_MB = 20

export interface AppConfig {
  maxUploadMb: number
}

export function readConfig(env: Record<string, unknown>): AppConfig {
  const maxUploadMb =
    typeof env.VITE_MAX_UPLOAD_MB === 'string' ? Number(env.VITE_MAX_UPLOAD_MB) : Number.NaN

  return {
    maxUploadMb: Number.isFinite(maxUploadMb) && maxUploadMb > 0 ? maxUploadMb : DEFAULT_MAX_UPLOAD_MB,
  }
}

export const config = readConfig(import.meta.env)
