import { useCallback } from 'react'
import { useWizard } from './context'

/**
 * Chạy một request làm đổi state và khoá điều hướng trong lúc nó chạy. Bộ đếm nằm trong reducer
 * nên các request chồng nhau không nhả khoá sớm; không component nào phải tự ghép cặp bật/tắt.
 */
export function useBusyRequest() {
  const { dispatch } = useWizard()

  return useCallback(
    async <T>(run: () => Promise<T>): Promise<T> => {
      dispatch({ type: 'requestStarted' })
      try {
        return await run()
      } finally {
        dispatch({ type: 'requestSettled' })
      }
    },
    [dispatch],
  )
}
