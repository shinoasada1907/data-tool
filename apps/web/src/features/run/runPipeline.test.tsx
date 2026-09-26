import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from '../../App'
import { pipelineResultFixture, pipelineSummaryFixture, problemFixture, validResultFixture } from '../../mocks/fixtures'
import {
  fieldRegion,
  nextButton,
  openRulesStep,
  runButton,
  saved,
  schemaGroupByName,
  stepButton,
  type User,
} from '../../test/flows'
import {
  gate,
  mockProcess,
  mockResult,
  mockSaveTransformations,
  mockSaveValidations,
  problemResponse,
  recordRequests,
} from '../../test/http'

const RESULT_HEADING = { level: 2, name: 'Kết quả & export' } as const
const RULES_HEADING = { level: 2, name: 'Biến đổi & kiểm tra' } as const

function processed() {
  return HttpResponse.json(pipelineSummaryFixture())
}

function resultPage() {
  return HttpResponse.json(pipelineResultFixture())
}

function problemWithErrors(status: number, code: string, errors: { field: string | null; code: string; message: string }[]) {
  return HttpResponse.json(problemFixture(status, code, 'Request failed.', { errors }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

async function toggleRule(user: User, fieldName: string, rule: 'email' | 'unique') {
  await user.click(within(fieldRegion(fieldName)).getByRole('checkbox', { name: new RegExp(rule) }))
}

async function addTrim(user: User, fieldName: string) {
  const region = fieldRegion(fieldName)
  await user.selectOptions(within(region).getByRole('combobox', { name: 'Loại biến đổi' }), 'trim')
  await user.click(within(region).getByRole('button', { name: 'Thêm biến đổi' }))
}

describe('Chạy xử lý', () => {
  test('lưu transformations, validations, chạy process, tải trang kết quả đầu rồi sang bước Kết quả', async () => {
    const transformations = mockSaveTransformations(saved)
    const validations = mockSaveValidations(saved)
    mockProcess(processed)
    mockResult(resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    await addTrim(user, 'Họ tên')
    await toggleRule(user, 'Họ tên', 'unique')
    const log = recordRequests()

    await user.click(runButton())

    expect(await screen.findByRole('heading', RESULT_HEADING)).toBeInTheDocument()
    // Có dòng lỗi (invalid: 20) nên trang đầu là tab Lỗi.
    expect(log).toEqual([
      'PUT transformations',
      'PUT validations',
      'POST process',
      'GET result?view=invalid&page=0&size=50',
    ])
    expect(transformations.bodies).toEqual([{ transformations: [{ targetField: 'Họ tên', order: 0, type: 'trim' }] }])
    expect(validations.bodies).toEqual([{ validations: [{ targetField: 'Họ tên', type: 'unique' }] }])
  })

  test('không có dòng lỗi thì trang đầu là tab Hợp lệ', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() => HttpResponse.json(pipelineSummaryFixture({ valid: 120, invalid: 0, errorCountsByCode: {}, errorCountsByField: {} })))
    const result = mockResult(() => HttpResponse.json(validResultFixture()))
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())

    await screen.findByRole('heading', RESULT_HEADING)
    expect(result.queries).toEqual([{ view: 'valid', page: '0', size: '50' }])
  })

  test('đổi tên field sau khi cấu hình rules: payload transformations và validations mang tên mới', async () => {
    const transformations = mockSaveTransformations(saved)
    const validations = mockSaveValidations(saved)
    mockProcess(processed)
    mockResult(resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    await addTrim(user, 'Họ tên')
    await toggleRule(user, 'Họ tên', 'email')

    await user.click(screen.getByRole('button', { name: 'Quay lại' }))
    await user.click(screen.getByRole('button', { name: 'Quay lại' }))
    const name = within(schemaGroupByName('Họ tên')).getByRole('textbox', { name: 'Tên field' })
    await user.clear(name)
    await user.type(name, 'Tên khách')
    await user.click(nextButton())
    await screen.findByRole('heading', { level: 2, name: 'Mapping' })
    await user.click(nextButton())
    await screen.findByRole('heading', RULES_HEADING)
    await user.click(runButton())

    await screen.findByRole('heading', RESULT_HEADING)
    expect(transformations.bodies).toEqual([{ transformations: [{ targetField: 'Tên khách', order: 0, type: 'trim' }] }])
    expect(validations.bodies).toEqual([{ validations: [{ targetField: 'Tên khách', type: 'email' }] }])
  })

  test('chạy lại sau khi sửa rule: chỉ gửi phần chưa lưu; kết quả mới thay kết quả cũ, không còn cảnh báo cũ', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(processed, () =>
      HttpResponse.json(pipelineSummaryFixture({ total: 120, valid: 90, invalid: 30 })),
    )
    mockResult(resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    await user.click(runButton())
    await screen.findByRole('heading', RESULT_HEADING)

    await user.click(stepButton(/Biến đổi & kiểm tra/))
    await toggleRule(user, 'Họ tên', 'unique')
    const log = recordRequests()
    await user.click(runButton())

    await screen.findByRole('heading', RESULT_HEADING)
    expect(log).toEqual(['PUT validations', 'POST process', 'GET result?view=invalid&page=0&size=50'])
    expect(screen.getByRole('group', { name: 'Tóm tắt kết quả' })).toHaveTextContent('30')
    expect(screen.queryByText(/kết quả này là của lần chạy trước/)).not.toBeInTheDocument()
  })

  test('PUT validations trả 422: không chạy process, lỗi tại khối field, transformation giữ trạng thái đã lưu', async () => {
    const transformations = mockSaveTransformations(saved)
    mockSaveValidations(
      () =>
        problemWithErrors(422, 'CONFIG_INVALID', [
          { field: 'Họ tên', code: 'CONFIG_INVALID', message: "Duplicate rule 'unique' for field 'Họ tên'." },
        ]),
      saved,
    )
    const process = mockProcess(processed)
    mockResult(resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    await toggleRule(user, 'Họ tên', 'unique')

    await user.click(runButton())

    expect(await screen.findByRole('alert')).toHaveTextContent('Cấu hình transformation hoặc validation không hợp lệ')
    expect(screen.getByRole('heading', RULES_HEADING)).toBeInTheDocument()
    expect(process.calls()).toBe(0)
    const fieldError = within(fieldRegion('Họ tên')).getByText("Duplicate rule 'unique' for field 'Họ tên'.")
    expect(fieldError).toHaveFocus()
    expect(fieldRegion('Họ tên')).toHaveAccessibleDescription("Duplicate rule 'unique' for field 'Họ tên'.")

    // Transformations đã lưu ở lần trước nên lần này không gửi lại.
    await user.click(runButton())
    await screen.findByRole('heading', RESULT_HEADING)
    expect(transformations.calls()).toBe(1)
  })

  test('process trả 409 SESSION_NOT_READY: khối lỗi liệt kê từng readiness issue theo field, ở lại bước', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() =>
      problemWithErrors(409, 'SESSION_NOT_READY', [
        { field: 'Email', code: 'TARGET_FIELD_REQUIRED', message: 'Required target field is not mapped.' },
        { field: null, code: 'SCHEMA_EMPTY', message: 'Target schema has no fields.' },
      ]),
    )
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Cấu hình chưa đủ để chạy xử lý')
    expect(within(alert).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Email: Field bắt buộc chưa được map',
      'Schema chưa có field nào',
    ])
    expect(screen.getByRole('heading', RULES_HEADING)).toHaveFocus()
  })

  test('process trả 409 SESSION_STATE_INVALID: nút "Upload lại" đưa về bước Upload', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() => problemResponse(409, 'SESSION_STATE_INVALID', 'Session is FAILED.'))
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('Phiên import không dùng được nữa')
    await user.click(screen.getByRole('button', { name: 'Upload lại' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
  })

  test('process trả 422 FILE_PARSE_ERROR: báo lỗi kèm detail và hiện ngay nút "Upload lại" (session đã FAILED)', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() => problemResponse(422, 'FILE_PARSE_ERROR', 'Malformed CSV at line 12.'))
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Không đọc được file')
    expect(alert).toHaveTextContent('Malformed CSV at line 12.')
    expect(within(alert).getByRole('button', { name: 'Upload lại' })).toBeInTheDocument()
  })

  test('process trả 500 INTERNAL_ERROR: lỗi chung, "Chạy xử lý" bấm lại được và lần sau không gửi lại PUT', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    const process = mockProcess(() => problemResponse(500, 'INTERNAL_ERROR', 'Unexpected error.'), processed)
    mockResult(resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    const log = recordRequests()

    await user.click(runButton())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Máy chủ gặp lỗi không lường trước')
    expect(within(alert).queryByRole('button', { name: 'Upload lại' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', RULES_HEADING)).toHaveFocus()
    expect(runButton()).toBeEnabled()

    await user.click(runButton())
    await screen.findByRole('heading', RESULT_HEADING)
    expect(process.calls()).toBe(2)
    expect(log.filter((entry) => entry.startsWith('PUT'))).toEqual(['PUT transformations', 'PUT validations'])
  })

  test('đang chạy: hiện "Đang xử lý…", khoá nút chạy, nút Quay lại và stepper; bấm đúp chỉ gửi một lượt', async () => {
    const transformations = mockSaveTransformations(saved)
    const hold = gate()
    const process = mockProcess(async () => {
      await hold.promise
      return processed()
    })
    mockSaveValidations(saved)
    mockResult(resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.dblClick(runButton())

    expect(await screen.findByText('Đang xử lý…', { selector: '[role="status"]' })).toBeInTheDocument()
    expect(runButton()).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Quay lại' })).toBeDisabled()
    expect(stepButton(/Mapping/)).toBeDisabled()
    hold.open()
    await screen.findByRole('heading', RESULT_HEADING)
    expect(transformations.calls()).toBe(1)
    expect(process.calls()).toBe(1)
  })

  test('sửa rule sau khi lưu lỗi: lỗi của field và khối lỗi biến mất (chúng nói về bản đã gửi)', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(() =>
      problemWithErrors(422, 'CONFIG_INVALID', [
        { field: 'Họ tên', code: 'CONFIG_INVALID', message: "Duplicate rule 'unique' for field 'Họ tên'." },
      ]),
    )
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    await toggleRule(user, 'Họ tên', 'unique')
    await user.click(runButton())
    await screen.findByRole('alert')

    await addTrim(user, 'Họ tên')

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(within(fieldRegion('Họ tên')).queryByText(/Duplicate rule/)).not.toBeInTheDocument()
  })

  test('sang bước Kết quả: focus ở tiêu đề của bước mới', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(processed)
    mockResult(resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())

    expect(await screen.findByRole('heading', RESULT_HEADING)).toHaveFocus()
  })
})
