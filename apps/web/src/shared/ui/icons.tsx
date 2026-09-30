import type { ReactNode } from 'react'

// Icon SVG dạng nét, màu theo `currentColor`. Luôn aria-hidden: icon chỉ để minh hoạ, chữ bên cạnh mới mang nghĩa.

function Icon({ size = 20, strokeWidth = 1.75, children }: { size?: number; strokeWidth?: number; children: ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  )
}

export function UploadIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M12 16V4" />
      <path d="m7 9 5-5 5 5" />
      <path d="M20 16v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3" />
    </Icon>
  )
}

/** Mũi tên đi vào khay: công cụ nhập dữ liệu. */
export function ImportIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M12 4v12" />
      <path d="m7 11 5 5 5-5" />
      <path d="M20 16v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3" />
    </Icon>
  )
}

/** Tờ giấy có dấu tích: công cụ kiểm tra dữ liệu. */
export function ValidateIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
      <path d="M14 3v6h6" />
      <path d="m9 15 2 2 4-4" />
    </Icon>
  )
}

export function ArrowRightIcon({ size = 18 }: { size?: number }) {
  return (
    <Icon size={size} strokeWidth={2.25}>
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </Icon>
  )
}

export function FileIcon({ size }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5" />
    </Icon>
  )
}

export function CheckIcon({ size, strokeWidth = 2.25 }: { size?: number; strokeWidth?: number }) {
  return (
    <Icon size={size} strokeWidth={strokeWidth}>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </Icon>
  )
}

export function AlertCircleIcon({ size }: { size?: number }) {
  return (
    <Icon size={size} strokeWidth={1.9}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5v5.5" />
      <path d="M12 16.5v.01" />
    </Icon>
  )
}

export function AlertTriangleIcon({ size }: { size?: number }) {
  return (
    <Icon size={size} strokeWidth={1.9}>
      <path d="M10.3 4.2 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z" />
      <path d="M12 9.5v4" />
      <path d="M12 17v.01" />
    </Icon>
  )
}

export function ArrowUpIcon({ size = 18 }: { size?: number }) {
  return (
    <Icon size={size} strokeWidth={2.25}>
      <path d="M12 19V5" />
      <path d="m6 11 6-6 6 6" />
    </Icon>
  )
}

export function ArrowDownIcon({ size = 18 }: { size?: number }) {
  return (
    <Icon size={size} strokeWidth={2.25}>
      <path d="M12 5v14" />
      <path d="m6 13 6 6 6-6" />
    </Icon>
  )
}

export function TrashIcon({ size = 18 }: { size?: number }) {
  return (
    <Icon size={size}>
      <path d="M4 7h16" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M6 7l1 13h10l1-13" />
      <path d="M9 7V4h6v3" />
    </Icon>
  )
}

export function PlusIcon({ size = 18 }: { size?: number }) {
  return (
    <Icon size={size} strokeWidth={2.25}>
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </Icon>
  )
}

/** Khung có cột bên trái: bật/tắt sidebar. */
export function PanelLeftIcon({ size = 20 }: { size?: number }) {
  return (
    <Icon size={size}>
      <rect x="3" y="4" width="18" height="16" rx="1" />
      <path d="M9 4v16" />
    </Icon>
  )
}
