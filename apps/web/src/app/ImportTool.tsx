import { WizardProvider } from '../wizard/WizardProvider'
import { WizardShell } from '../wizard/WizardShell'

/** Công cụ "Import dữ liệu": wizard 6 bước của change fe-import-wizard-v0-1. */
export function ImportTool() {
  return (
    <WizardProvider>
      <WizardShell />
    </WizardProvider>
  )
}
