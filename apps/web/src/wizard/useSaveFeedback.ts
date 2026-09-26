import { useCallback, useState } from 'react'
import { flushSync } from 'react-dom'
import { ApiError, isSessionUnusable } from '../api/apiError'
import { matchServerErrors } from '../domain/schemaRules'
import type { FieldKey, TargetField } from '../domain/types'
import { describeApiError, type ErrorText } from '../shared/describeError'
import { messages } from '../shared/messages'

export interface SaveFailure {
  text: ErrorText
  /** `errors[]` của BE không gắn được vào field nào. */
  items: string[]
  /** Session hết hạn hoặc hỏng: chỉ còn cách upload lại (design D12). */
  reupload: boolean
}

/**
 * Phản hồi sau một lần lưu cấu hình (PUT schema, mapping…), dùng chung cho các bước để cùng một luật (review FE-F05):
 * lỗi BE gắn theo field, khối lỗi ở đầu bước, và nơi đặt focus sau khi lưu lỗi.
 */
export function useSaveFeedback() {
  const [serverErrors, setServerErrors] = useState<Record<FieldKey, string>>({})
  const [failure, setFailure] = useState<SaveFailure | null>(null)

  /** Trước mỗi lần lưu: lỗi cũ không còn đúng nữa. */
  const clearBeforeSave = useCallback(() => {
    setFailure(null)
    setServerErrors({})
  }, [])

  /**
   * Khi user sửa bất kỳ field nào: lỗi BE và khối lỗi nói về bản đã gửi, bản đó không còn nữa. Riêng lỗi session hỏng
   * thì sửa gì cũng không hết, nên giữ nút "Upload lại".
   */
  const clearOnEdit = useCallback(() => {
    setServerErrors({})
    setFailure((current) => (current?.reupload ? current : null))
  }, [])

  /**
   * Báo lỗi lưu. `fields` là đúng bản đã gửi (để ghép `errors[].field` theo tên đã gửi). `focusField(key)` đưa focus
   * tới control đang mang lỗi của field đó và trả true nếu làm được; không thì focus `fallback` (tiêu đề bước).
   */
  const report = useCallback(
    (
      error: unknown,
      fields: readonly TargetField[],
      focusField: (key: FieldKey) => boolean,
      fallback: HTMLElement | null,
    ) => {
      if (!(error instanceof ApiError)) {
        // Lỗi lập trình: user chỉ thấy câu chung, nên stack phải nằm ở console.
        console.error(error)
        setFailure({ text: { headline: messages.unexpected }, items: [], reupload: false })
        fallback?.focus()
        return
      }
      const matched = matchServerErrors(fields, error.fieldErrors)
      // Gắn lỗi vào DOM trước rồi mới focus: screen reader đọc control lúc nó nhận focus (design D14).
      flushSync(() => {
        setServerErrors(matched.byKey)
        setFailure({
          text: describeApiError(error) ?? { headline: messages.unexpected },
          items: matched.general,
          reupload: isSessionUnusable(error),
        })
      })
      // Nút "Tiếp" bị khoá trong lúc lưu nên đã mất focus: đưa tới field lỗi đầu tiên, hoặc tiêu đề bước.
      const firstInvalid = fields.find((field) => matched.byKey[field.key])
      if (!firstInvalid || !focusField(firstInvalid.key)) fallback?.focus()
    },
    [],
  )

  return { serverErrors, failure, clearBeforeSave, clearOnEdit, report }
}
