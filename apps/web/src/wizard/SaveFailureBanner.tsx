import { messages } from '../shared/messages'
import { ErrorBanner } from '../shared/ui/ErrorBanner'
import { useWizard } from './context'
import type { SaveFailure } from './useSaveFeedback'

/** Khối lỗi ở đầu bước sau một lần lưu lỗi; session hỏng thì có nút "Upload lại" (design D12). */
export function SaveFailureBanner({ failure }: { failure: SaveFailure }) {
  const { dispatch } = useWizard()

  return (
    <ErrorBanner
      text={failure.text}
      items={failure.items}
      action={
        failure.reupload
          ? { label: messages.sessionUnusableAction, onClick: () => dispatch({ type: 'reset' }) }
          : undefined
      }
    />
  )
}
