import { useState, type RefObject } from 'react'
import { ApiError } from '../../api/apiError'
import { getResult, postProcess, putTransformations, putValidations } from '../../api/endpoints'
import { toPipelineSummary, toResultPage, toTransformationConfigDto, toValidationConfigDto } from '../../api/mappers'
import { checkTransformations } from '../../domain/configRules'
import { normalizeFieldName } from '../../domain/schemaRules'
import type { FieldKey, ResultQuery } from '../../domain/types'
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
 * ở bước Kết quả: PUT transformations và validations nếu chưa lưu, POST process, rồi GET trang kết quả đầu. Dừng ở
 * bước lỗi đầu tiên; phần đã lưu thành công trước đó giữ trạng thái đã lưu.
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
        const summary = toPipelineSummary(await postProcess(session.id))
        const query: ResultQuery = { view: summary.invalid > 0 ? 'invalid' : 'valid', page: 0, field: null, code: null }
        const page = toResultPage(await getResult(session.id, query))
        const columns = fields.map((field) => normalizeFieldName(field.name))
        dispatch({ type: 'processCompleted', summary, columns, query, page })
      })
    } catch (error) {
      reportFailure(error)
    } finally {
      setRunning(false)
    }

    function reportFailure(error: unknown) {
      const shownFields = focusField ? fields : []
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
      // Process gặp FILE_PARSE_ERROR thì session đã FAILED: chạy lại vô ích, chỉ còn cách upload lại (design D12).
      const reupload = error instanceof ApiError && error.code === 'FILE_PARSE_ERROR' ? true : undefined
      report(error, shownFields, focus, headingRef.current, { reupload })
    }
  }

  return { run, running, blockedReason, serverErrors, failure, clearOnEdit }
}

/** Lý do không chạy được: thiếu schema/mapping đã lưu, hoặc còn tham số transformation thiếu (spec pipeline-run). */
export function runBlockedReason(state: WizardState): string | undefined {
  const guard = canEnter('rules', state)
  if (!guard.allowed) return guard.reason
  return checkTransformations(state.schema.draft, state.transformations.draft).blockedReason ?? undefined
}
