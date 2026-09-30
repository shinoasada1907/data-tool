import { useMemo, useReducer, type ReactNode } from 'react'
import { WizardContext } from './context'
import { wizardReducer } from './reducer'
import { initialWizardState } from './state'

export function WizardProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(wizardReducer, initialWizardState)
  const value = useMemo(() => ({ state, dispatch }), [state])

  return <WizardContext value={value}>{children}</WizardContext>
}
