import { screen, within } from '@testing-library/react'
import type userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import type { PipelineResultDto, PipelineSummaryDto } from '../api/dto'
import {
  configUpdateFixture,
  importSessionFixture,
  pipelineResultFixture,
  pipelineSummaryFixture,
  validResultFixture,
} from '../mocks/fixtures'
import { testFile } from './files'
import {
  mockProcess,
  mockResult,
  mockSaveMapping,
  mockSaveSchema,
  mockSaveTransformations,
  mockSaveValidations,
  mockUpload,
  type ResultResponder,
} from './http'

// Các bước đi qua wizard dùng chung cho test của nhiều bước. Preview mặc định (csvPreviewFixture) sinh 4 field:
// "2024" (string), "Họ tên" (string), "1" (number), "Email" (email).

export type User = ReturnType<typeof userEvent.setup>

export const saved = () => HttpResponse.json(configUpdateFixture())

export const RULES_HEADING = { level: 2, name: 'Biến đổi & kiểm tra' } as const
export const RESULT_HEADING = { level: 2, name: 'Kết quả & export' } as const

export function nextButton() {
  return screen.getByRole('button', { name: /^Tiếp/ })
}

export function runButton() {
  return screen.getByRole('button', { name: /^Chạy xử lý/ })
}

/** Nút của một bước trên stepper. */
export function stepButton(name: RegExp) {
  return within(screen.getByRole('navigation', { name: 'Các bước import' })).getByRole('button', { name })
}

/** Khối của một field ở bước Biến đổi & kiểm tra. */
export function fieldRegion(name: string) {
  return screen.getByRole('group', { name })
}

/** Nhóm của một field ở bước Schema, tìm theo tên đang nhập. */
export function schemaGroupByName(name: string) {
  return screen
    .getAllByRole('group', { name: /^Field \d+$/ })
    .find((group) => (within(group).getByRole('textbox', { name: 'Tên field' }) as HTMLInputElement).value === name)!
}

/**
 * Upload → Schema (đánh dấu bắt buộc / đổi kiểu theo tên) → lưu → Mapping (mặc định) → lưu → Biến đổi & kiểm tra.
 * Test tự render `<App />` trước khi gọi.
 */
export async function openRulesStep(
  user: User,
  { required = [], types = {} }: { required?: string[]; types?: Record<string, string> } = {},
) {
  mockUpload(() => HttpResponse.json(importSessionFixture(), { status: 201 }))
  mockSaveSchema(saved)
  mockSaveMapping(saved)
  await user.upload(screen.getByLabelText('Chọn file CSV hoặc XLSX'), testFile('khach-hang.csv'))
  await screen.findByRole('table', { name: 'Dữ liệu xem trước' })
  await user.click(nextButton())
  await screen.findByRole('heading', { level: 2, name: 'Schema đích' })
  for (const name of required) {
    await user.click(within(schemaGroupByName(name)).getByRole('checkbox', { name: 'Bắt buộc' }))
  }
  for (const [name, type] of Object.entries(types)) {
    await user.selectOptions(within(schemaGroupByName(name)).getByRole('combobox', { name: 'Kiểu' }), type)
  }
  await user.click(nextButton())
  await screen.findByRole('heading', { level: 2, name: 'Mapping' })
  await user.click(nextButton())
  await screen.findByRole('heading', { level: 2, name: 'Biến đổi & kiểm tra' })
}

export async function addStep(user: User, fieldName: string, type: string) {
  const region = fieldRegion(fieldName)
  await user.selectOptions(within(region).getByRole('combobox', { name: 'Loại biến đổi' }), type)
  await user.click(within(region).getByRole('button', { name: 'Thêm biến đổi' }))
}

/** Trả trang theo `view` của query: tab Lỗi dùng `invalid`, tab Hợp lệ dùng `valid`; số trang lấy từ query. */
export function pagesByView({
  invalid = pipelineResultFixture(),
  valid = validResultFixture(),
}: { invalid?: PipelineResultDto; valid?: PipelineResultDto } = {}): ResultResponder {
  return (query) => {
    const base = query.view === 'valid' ? valid : invalid
    return HttpResponse.json({ ...base, page: { ...base.page, number: Number(query.page) } })
  }
}

/**
 * … → Biến đổi & kiểm tra → "Chạy xử lý" → bước Kết quả (trang đầu đã tải). `result` trả mọi lần GET result.
 * Test tự render `<App />` trước khi gọi.
 */
export async function openResultStep(
  user: User,
  { summary = pipelineSummaryFixture(), result = pagesByView() }: { summary?: PipelineSummaryDto; result?: ResultResponder } = {},
) {
  mockSaveTransformations(saved)
  mockSaveValidations(saved)
  mockProcess(() => HttpResponse.json(summary))
  const requests = mockResult(result)
  await openRulesStep(user)
  await user.click(runButton())
  await screen.findByRole('heading', RESULT_HEADING)
  return requests
}
