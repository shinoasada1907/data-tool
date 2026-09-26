import type { ProblemItemDto } from './dto'

export type ApiErrorKind = 'http' | 'network' | 'aborted'

interface ApiErrorInit {
  kind: ApiErrorKind
  status?: number | null
  code?: string | null
  title?: string | null
  detail?: string | null
  fieldErrors?: ProblemItemDto[]
}

/** Mọi lỗi khi gọi API đều được chuẩn hoá về dạng này (design → "Xử lý lỗi"). */
export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number | null
  readonly code: string | null
  readonly title: string | null
  readonly detail: string | null
  readonly fieldErrors: ProblemItemDto[]

  constructor(init: ApiErrorInit) {
    super(init.kind === 'http' ? `HTTP ${init.status ?? '?'}${init.code ? ` ${init.code}` : ''}` : init.kind)
    this.name = 'ApiError'
    this.kind = init.kind
    this.status = init.status ?? null
    this.code = init.code ?? null
    this.title = init.title ?? null
    this.detail = init.detail ?? null
    this.fieldErrors = init.fieldErrors ?? []
  }
}

export function errorFromHttpResponse(status: number, contentType: string | null, body: string): ApiError {
  const problem = contentType?.includes('json') ? parseProblem(body) : null

  return new ApiError({
    kind: 'http',
    status,
    // BE luôn gửi code; body không phải JSON chỉ đến từ tầng khác (proxy), riêng 413 vẫn đoán được ý nghĩa.
    code: problem?.code ?? (status === 413 ? 'FILE_TOO_LARGE' : null),
    title: problem?.title,
    detail: problem?.detail,
    fieldErrors: problem?.errors,
  })
}

function parseProblem(body: string) {
  let value: unknown
  try {
    value = JSON.parse(body)
  } catch {
    return null
  }
  if (!isRecord(value)) return null

  return {
    code: stringOrNull(value.code),
    title: stringOrNull(value.title),
    detail: stringOrNull(value.detail),
    errors: Array.isArray(value.errors) ? value.errors.flatMap(toProblemItem) : [],
  }
}

function toProblemItem(item: unknown): ProblemItemDto[] {
  if (!isRecord(item) || typeof item.code !== 'string' || typeof item.message !== 'string') return []
  return [{ field: stringOrNull(item.field), code: item.code, message: item.message }]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringOrNull(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}
