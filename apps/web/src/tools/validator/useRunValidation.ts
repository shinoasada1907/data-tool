import { ApiError } from '../../api/apiError'
import { sourceOf } from '../../shared/dataset/datasetApi'
import { datasetMessages } from '../../shared/dataset/messages'
import { describeApiError } from '../../shared/describeError'
import { messages } from '../../shared/messages'
import { useValidator } from './context'
import { describeItem, problemsFromPointers, toSchemaDto, type SchemaDraft } from './schema'
import type { RunFailure } from './state'
import { createRun, deleteRun } from './validatorApi'

/** "Chạy kiểm tra" và "Chạy lại": gửi nguồn (đúng cách đọc của preview) cùng schema; run cũ được dọn (design V8). */
export function useRunValidation() {
  const { state, dispatch } = useValidator()

  return async function run() {
    const { preview, schema, revision, result, running } = state
    if (!preview || running) return
    dispatch({ type: 'runStarted' })
    try {
      const created = await createRun(sourceOf(preview), toSchemaDto(schema))
      if (result) deleteRun(result.run.id)
      dispatch({ type: 'runCompleted', run: created, revision })
    } catch (error) {
      dispatch({ type: 'runFailed', failure: failureOf(error, schema) })
    }
  }
}

function failureOf(error: unknown, schema: SchemaDraft): RunFailure {
  if (!(error instanceof ApiError)) return { text: { headline: messages.unexpected }, problems: null, items: [] }
  const text = describeApiError(error, datasetMessages.errorOverrides) ?? { headline: messages.unexpected }

  if (error.code === 'SCHEMA_INVALID') {
    const problems = problemsFromPointers(schema, error.fieldErrors)
    return { text, problems, items: problems.general }
  }
  const items = error.fieldErrors.map((item) => (item.field ? `${item.field}: ${describeItem(item)}` : describeItem(item)))
  return { text, problems: null, items }
}
