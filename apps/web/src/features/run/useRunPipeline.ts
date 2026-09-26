import { useState, type RefObject } from 'react'
import { ApiError, isSessionUnusable } from '../../api/apiError'
import { getSessionStatus, postProcess, putTransformations, putValidations } from '../../api/endpoints'
import { toPipelineSummary, toTransformationConfigDto, toValidationConfigDto } from '../../api/mappers'
import { checkTransformations } from '../../domain/configRules'
import { normalizeFieldName } from '../../domain/schemaRules'
import type { FieldKey, PipelineSummary, ResultQuery } from '../../domain/types'
import { codeMessage } from '../../shared/describeError'
import { useWizard } from '../../wizard/context'
import { canEnter } from '../../wizard/guards'
import type { WizardState } from '../../wizard/state'
import { useBusyRequest } from '../../wizard/useBusyRequest'
import { useSaveFeedback } from '../../wizard/useSaveFeedback'

interface RunPipelineOptions {
  /** Tiêu đề bước: nhận focus khi lỗi không gắn được vào field nào. */
  headingRef: RefObject<HTMLElement | null>
  /**
   * Đưa focus tới lỗi đang hiển thị của một field, trả true nếu làm được. Bước không hiển thị lỗi theo field (Kết quả)
   * thì không truyền: lỗi theo field khi đó nằm trong danh sách của khối lỗi.
   */
  focusField?: (key: FieldKey) => boolean
}

/**
 * Trình tự "Chạy xử lý" (spec pipeline-run), dùng chung cho nút "Chạy xử lý" ở bước Biến đổi & kiểm tra và "Chạy lại"
 * ở bước Kết quả: PUT transformations và validations nếu chưa lưu, rồi POST process. Dừng ở bước lỗi đầu tiên; phần
 * đã lưu thành công trước đó giữ trạng thái đã lưu. Process xong thì sang bước Kết quả, và bước đó tự tải trang đầu.
 */
export function useRunPipeline({ headingRef, focusField }: RunPipelineOptions) {
  const { state, dispatch } = useWizard()
  const runBusy = useBusyRequest()
  const { serverErrors, failure, clearBeforeSave, clearOnEdit, report } = useSaveFeedback()
  const [running, setRunning] = useState(false)
  const { session, schema, transformations, validations } = state
  const blockedReason = runBlockedReason(state)

  async function run() {
    if (!session || blockedReason !== undefined) return
    setRunning(true)
    clearBeforeSave()
    // Đúng bản đang có lúc bấm: phần sửa bị khoá trong lúc chạy, và `sectionSaved` so tham chiếu với bản này.
    const fields = schema.draft
    let sessionFailed: boolean | undefined
    try {
      // runBusy tăng bộ đếm bận ngay trong sự kiện click, trước lần `await` đầu, nên nút đã khoá trước cú bấm thứ hai
      // của bấm đúp (spec pipeline-run, "Bấm đúp"; có test).
      await runBusy(async () => {
        if (!transformations.saved) {
          await putTransformations(session.id, toTransformationConfigDto(fields, transformations.draft))
          dispatch({ type: 'sectionSaved', section: 'transformations', draft: transformations.draft })
        }
        if (!validations.saved) {
          await putValidations(session.id, toValidationConfigDto(fields, validations.draft))
          dispatch({ type: 'sectionSaved', section: 'validations', draft: validations.draft })
        }
        let summary: PipelineSummary
        try {
          summary = toPipelineSummary(await postProcess(session.id))
        } catch (error) {
          const outcome = await afterProcessFailure(session.id, error)
          sessionFailed = outcome.sessionFailed
          if (!outcome.resultKept) {
            dispatch({ type: 'resultUnavailable', reason: outcome.sessionFailed ? 'sessionUnusable' : 'unavailable' })
          }
          throw error
        }
        const query: ResultQuery = { view: summary.invalid > 0 ? 'invalid' : 'valid', page: 0, field: null, code: null }
        const columns = fields.map((field) => normalizeFieldName(field.name))
        dispatch({ type: 'processCompleted', summary, columns, query })
      })
    } catch (error) {
      reportFailure(error)
    } finally {
      setRunning(false)
    }

    function reportFailure(error: unknown) {
      const focus = focusField ?? (() => false)
      if (error instanceof ApiError && error.code === 'SESSION_NOT_READY') {
        // Readiness issue là danh sách việc cần làm, không phải lỗi của ô nào: liệt kê trong khối lỗi, bằng thông
        // điệp FE theo mã (message của BE là tiếng Anh).
        const fieldErrors = error.fieldErrors.map((item) => ({
          field: item.field,
          message: codeMessage(item.code) ?? item.message,
        }))
        report(error, [], focus, headingRef.current, { fieldErrors })
        return
      }
      report(error, focusField ? fields : [], focus, headingRef.current, { reupload: sessionFailed })
    }
  }

  return { run, running, blockedReason, serverErrors, failure, clearOnEdit }
}

interface ProcessFailureOutcome {
  /** true: session đã FAILED hoặc không còn, chỉ còn cách upload lại; undefined: theo luật chung (`isSessionUnusable`). */
  sessionFailed: boolean | undefined
  /** BE còn giữ kết quả của lần chạy trước, nên kết quả đang có trên FE vẫn dùng được. */
  resultKept: boolean
}

/**
 * Process lỗi thì BE có thể đã xoá kết quả cũ (design D12, D18; BE-F08):
 * - `SESSION_NOT_FOUND`, `SESSION_STATE_INVALID`: session không dùng được nữa.
 * - 422: mọi lỗi đọc file nguồn của process (BE bọc chúng lại), session thành `FAILED` và kết quả bị xoá.
 * - 5xx: cùng một `500 INTERNAL_ERROR` cho ba nguyên nhân: đọc file lỗi (session `FAILED`), lưu kết quả lỗi hoặc bug
 *   (session giữ nguyên, kết quả cũ còn). Body không phân biệt được, nên hỏi lại trạng thái session.
 * - Lỗi khác (409, 404, hết giờ, mất mạng): không biết BE đã làm gì, coi như kết quả không còn; sai thì chỉ tốn một lần
 *   chạy lại.
 */
async function afterProcessFailure(sessionId: string, error: unknown): Promise<ProcessFailureOutcome> {
  if (!(error instanceof ApiError) || error.kind !== 'http' || error.status === null) {
    return { sessionFailed: undefined, resultKept: false }
  }
  if (isSessionUnusable(error) || error.status === 422) return { sessionFailed: true, resultKept: false }
  if (error.status < 500) return { sessionFailed: undefined, resultKept: false }
  try {
    const status = await getSessionStatus(sessionId)
    return { sessionFailed: status === 'FAILED', resultKept: status === 'PROCESSED' }
  } catch (statusError) {
    // Session hết hạn giữa hai request: lượt hỏi trạng thái trả 404, và đó mới là lỗi quyết định.
    const gone = statusError instanceof ApiError && isSessionUnusable(statusError)
    return { sessionFailed: gone ? true : undefined, resultKept: false }
  }
}

/** Lý do không chạy được: thiếu schema/mapping đã lưu, hoặc còn tham số transformation thiếu (spec pipeline-run). */
export function runBlockedReason(state: WizardState): string | undefined {
  const guard = canEnter('rules', state)
  if (!guard.allowed) return guard.reason
  return checkTransformations(state.schema.draft, state.transformations.draft).blockedReason ?? undefined
}
