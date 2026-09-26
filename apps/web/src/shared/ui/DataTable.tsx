import { Fragment, useId, type Key, type ReactNode } from 'react'
import { messages } from '../messages'
import styles from './DataTable.module.css'

export interface DataTableColumn<Row> {
  key: Key
  header: ReactNode
  cell: (row: Row) => ReactNode
  /** Cột số dòng: là row header (`<th scope="row">`), chữ mono, dính bên trái khi cuộn ngang. */
  rowNumber?: boolean
  /** Ô có vấn đề ở dòng này (ví dụ field bị lỗi): nền đỏ nhạt, kèm chữ ẩn `flaggedLabel` cho screen reader. */
  flagged?: (row: Row) => boolean
}

interface DataTableProps<Row> {
  /** Caption của bảng; cũng là tên của vùng cuộn. */
  label: string
  columns: DataTableColumn<Row>[]
  rows: readonly Row[]
  rowKey: (row: Row) => Key
  /** Chữ ẩn đọc kèm ô bị đánh dấu (`flagged`). */
  flaggedLabel?: string
  /** Nội dung chi tiết đặt ngay dưới một dòng, trải hết chiều ngang; null thì dòng đó không có chi tiết. */
  detail?: (row: Row) => ReactNode
}

/**
 * Bảng dữ liệu với cột khai báo tường minh (thứ tự cột do mảng `columns` quyết định). Bảng rộng cuộn ngang
 * trong khung của nó; khung nhận focus để cuộn được bằng bàn phím.
 */
export function DataTable<Row>({ label, columns, rows, rowKey, flaggedLabel, detail }: DataTableProps<Row>) {
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
          {rows.map((row) => {
            const rowDetail = detail?.(row)
            return (
              <Fragment key={rowKey(row)}>
                <tr>
                  {columns.map((column) => {
                    if (column.rowNumber) {
                      return (
                        <th key={column.key} scope="row" data-row-number>
                          {column.cell(row)}
                        </th>
                      )
                    }
                    const flagged = column.flagged?.(row) ?? false
                    return (
                      <td key={column.key} data-flagged={flagged || undefined}>
                        <span className={styles.value}>
                          {column.cell(row)}
                          {flagged && flaggedLabel && <span className="sr-only">{` (${flaggedLabel})`}</span>}
                        </span>
                      </td>
                    )
                  })}
                </tr>
                {rowDetail != null && (
                  <tr data-detail>
                    <td colSpan={columns.length}>{rowDetail}</td>
                  </tr>
                )}
              </Fragment>
            )
          })}
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
