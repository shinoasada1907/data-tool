import { useId } from 'react'
import { OUTPUT_FORMATS, type OutputFormat } from './formats'
import styles from './OutputFormatPicker.module.css'

interface OutputFormatPickerProps {
  label: string
  value: OutputFormat
  disabled?: boolean
  onChange: (format: OutputFormat) => void
}

/** Chọn định dạng file xuất (`OutputDto.format` của toolbox); các tuỳ chọn khác dùng mặc định của BE (design V9). */
export function OutputFormatPicker({ label, value, disabled = false, onChange }: OutputFormatPickerProps) {
  const name = useId()
  return (
    <fieldset className={styles.picker} disabled={disabled}>
      <legend>{label}</legend>
      {OUTPUT_FORMATS.map((format) => (
        <label key={format} className={styles.choice}>
          <input
            type="radio"
            name={name}
            value={format}
            checked={value === format}
            onChange={() => onChange(format)}
          />
          <span>{format}</span>
        </label>
      ))}
    </fieldset>
  )
}
