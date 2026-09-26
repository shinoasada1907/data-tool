import { useId, type Key, type ReactNode } from 'react'
import { messages } from '../messages'
import styles from './DataTable.module.css'

export interface DataTableColumn<Row> {
  key: Key
  header: ReactNode
  cell: (row: Row) => ReactNode
  /** Cột số dòng: là row header (`<th scope="row">`), chữ mono, dính bên trái khi cuộn ngang. */
  rowNumber?: boolean
}

interface DataTableProps<Row> {
  /** Caption của bảng; cũng là tên của vùng cuộn. */
  label: string
  columns: DataTableColumn<Row>[]
  rows: Row[]
  rowKey: (row: Row) => Key
}

/**
 * Bảng dữ liệu với cột khai báo tường minh (thứ tự cột do mảng `columns` quyết định). Bảng rộng cuộn ngang
 * trong khung của nó; khung nhận focus để cuộn được bằng bàn phím.
 */
export function DataTable<Row>({ label, columns, rows, rowKey }: DataTableProps<Row>) {
  const captionId = useId()

  return (
    <div role="region" aria-labelledby={captionId} tabIndex={0} className={styles.scroll}>
      <table className={styles.table}>
        <caption id={captionId} className="sr-only">
          {label}
        </caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col" data-row-number={column.rowNumber || undefined}>
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column) =>
                column.rowNumber ? (
                  <th key={column.key} scope="row" data-row-number>
                    {column.cell(row)}
                  </th>
                ) : (
                  <td key={column.key}>
                    <span className={styles.value}>{column.cell(row)}</span>
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Ô không có giá trị (null): gạch ngang mờ, screen reader đọc "Ô trống". Không bao giờ hiện chữ "null". */
export function EmptyCell() {
  return (
    <span className={styles.emptyCell}>
      <span aria-hidden="true">—</span>
      <span className="sr-only">{messages.emptyCell}</span>
    </span>
  )
}
