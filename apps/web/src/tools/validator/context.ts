import { createContext, useContext, type Dispatch } from 'react'
import type { ValidatorAction, ValidatorState } from './state'

export const ValidatorContext = createContext<{ state: ValidatorState; dispatch: Dispatch<ValidatorAction> } | null>(null)

export function useValidator() {
  const value = useContext(ValidatorContext)
  if (!value) throw new Error('useValidator phải nằm trong ValidatorTool')
  return value
}
