import { setupWorker } from 'msw/browser'
import { createDevHandlers } from './devHandlers'

/** MSW worker cho `pnpm dev:mock`; chỉ `main.tsx` import động file này, nên bản build thường không có nó. */
export const worker = setupWorker(...createDevHandlers())
