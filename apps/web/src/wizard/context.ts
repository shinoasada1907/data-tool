import { createContext, useContext, type Dispatch } from 'react'
import type { WizardAction, WizardState } from './state'

export interface WizardContextValue {
  state: WizardState
  dispatch: Dispatch<WizardAction>
}

export const WizardContext = createContext<WizardContextValue | null>(null)

export function useWizard(): WizardContextValue {
  const value = useContext(WizardContext)
  if (!value) throw new Error('useWizard phải được gọi bên trong <WizardProvider>')
  return value
}
