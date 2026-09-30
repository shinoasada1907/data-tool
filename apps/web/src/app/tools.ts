import type { ComponentType } from 'react'
import { messages } from '../shared/messages'
import { ImportIcon, ValidateIcon } from '../shared/ui/icons'
import { ValidatorTool } from '../tools/validator/ValidatorTool'
import { ImportTool } from './ImportTool'

export interface ToolDefinition {
  id: string
  label: string
  /** Một dòng mô tả, hiện dưới tiêu đề trang. */
  description: string
  Icon: ComponentType
  Component: ComponentType
}

/** Các công cụ của app, theo thứ tự trên sidebar. Thêm công cụ mới = thêm một phần tử (design D1). */
export const tools: ToolDefinition[] = [
  {
    id: 'import',
    label: messages.tools.import.label,
    description: messages.tools.import.description,
    Icon: ImportIcon,
    Component: ImportTool,
  },
  {
    id: 'validator',
    label: messages.tools.validator.label,
    description: messages.tools.validator.description,
    Icon: ValidateIcon,
    Component: ValidatorTool,
  },
]
