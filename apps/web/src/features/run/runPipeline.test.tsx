import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse } from 'msw'
import { describe, expect, test } from 'vitest'
import App from '../../App'
import {
  importSessionFixture,
  SESSION_ID,
  pipelineResultFixture,
  pipelineSummaryFixture,
  validResultFixture,
} from '../../mocks/fixtures'
import {
  addStep,
  fieldRegion,
  nextButton,
  openRulesStep,
  RESULT_HEADING,
  RULES_HEADING,
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
  mockSession,
  problemResponse,
  problemWithErrors,
  recordRequests,
} from '../../test/http'

function processed() {
  return HttpResponse.json(pipelineSummaryFixture())
}

function resultPage() {
  return HttpResponse.json(pipelineResultFixture())
}

async function toggleRule(user: User, fieldName: string, rule: 'email' | 'unique') {
  await user.click(within(fieldRegion(fieldName)).getByRole('checkbox', { name: new RegExp(rule) }))
}

/** Đã chạy thành công một lần rồi quay lại bước Biến đổi & kiểm tra bằng stepper (kết quả còn mới). */
async function runOnceAndGoBack(user: User) {
  await user.click(runButton())
  await screen.findByRole('heading', RESULT_HEADING)
  await user.click(stepButton(/Biến đổi & kiểm tra/))
  await screen.findByRole('heading', RULES_HEADING)
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
    await addStep(user, 'Họ tên', 'trim')
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
    await addStep(user, 'Họ tên', 'trim')
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

  test('process trả 500 mà session vẫn dùng được: lỗi chung, không có "Upload lại"; bấm lại chỉ gửi process', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    const process = mockProcess(() => problemResponse(500, 'INTERNAL_ERROR', 'Unexpected error.'), processed)
    mockSession(() => HttpResponse.json(importSessionFixture({ status: 'READY' })))
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
    // 500 của process không cho biết session còn dùng được không (BE-F08): FE hỏi lại trạng thái session.
    expect(log).toEqual(['PUT transformations', 'PUT validations', 'POST process', `GET ${SESSION_ID}`])

    await user.click(runButton())
    await screen.findByRole('heading', RESULT_HEADING)
    expect(process.calls()).toBe(2)
    expect(log.filter((entry) => entry.startsWith('PUT'))).toEqual(['PUT transformations', 'PUT validations'])
  })

  test('process trả 500 và session đã FAILED: hiện ngay "Upload lại"', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() => problemResponse(500, 'INTERNAL_ERROR', 'Source file could not be read.'))
    mockSession(() => HttpResponse.json(importSessionFixture({ status: 'FAILED' })))
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Máy chủ gặp lỗi không lường trước')
    expect(within(alert).getByRole('button', { name: 'Upload lại' })).toBeInTheDocument()
  })

  test('process trả 500, không hỏi được trạng thái session: vẫn là lỗi chung', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() => problemResponse(500, 'INTERNAL_ERROR', 'Unexpected error.'))
    mockSession(() => HttpResponse.error())
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Máy chủ gặp lỗi không lường trước')
    expect(within(alert).queryByRole('button', { name: 'Upload lại' })).not.toBeInTheDocument()
  })

  test('process trả 404 SESSION_NOT_FOUND: nút "Upload lại"', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    mockProcess(() => problemResponse(404, 'SESSION_NOT_FOUND', 'Import session not found.'))
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)

    await user.click(runButton())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Không tìm thấy phiên import')
    expect(within(alert).getByRole('button', { name: 'Upload lại' })).toBeInTheDocument()
  })

  test('PUT transformations lỗi: dừng ngay, không gửi validations hay process', async () => {
    mockSaveTransformations(() => problemResponse(500, 'INTERNAL_ERROR', 'Unexpected error.'))
    const validations = mockSaveValidations(saved)
    const process = mockProcess(processed)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    await toggleRule(user, 'Họ tên', 'unique')

    await user.click(runButton())

    expect(await screen.findByRole('alert')).toHaveTextContent('Máy chủ gặp lỗi không lường trước')
    expect(validations.calls()).toBe(0)
    expect(process.calls()).toBe(0)
  })

  describe('kết quả cũ khi process lỗi', () => {
    test('đã có kết quả, process trả 422 FILE_PARSE_ERROR (BE xoá kết quả): bước Kết quả hiện kết quả là cũ', async () => {
      mockSaveTransformations(saved)
      mockSaveValidations(saved)
      mockProcess(processed, () => problemResponse(422, 'FILE_PARSE_ERROR', 'Malformed CSV at line 12.'))
      mockResult(resultPage)
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)
      await runOnceAndGoBack(user)

      await user.click(runButton())
      await screen.findByRole('alert')
      await user.click(stepButton(/Kết quả/))

      expect(await screen.findByText('Cấu hình đã thay đổi — kết quả này là của lần chạy trước')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
    })

    test('đã có kết quả, process trả 500 nhưng session vẫn PROCESSED (BE giữ kết quả cũ): kết quả không bị coi là cũ', async () => {
      mockSaveTransformations(saved)
      mockSaveValidations(saved)
      mockProcess(processed, () => problemResponse(500, 'INTERNAL_ERROR', 'The result could not be stored.'))
      mockSession(() => HttpResponse.json(importSessionFixture({ status: 'PROCESSED' })))
      mockResult(resultPage)
      const user = userEvent.setup()
      render(<App />)
      await openRulesStep(user)
      await runOnceAndGoBack(user)

      await user.click(runButton())
      await screen.findByRole('alert')
      await user.click(stepButton(/Kết quả/))

      await screen.findByRole('heading', RESULT_HEADING)
      expect(screen.queryByText(/kết quả này là của lần chạy trước/)).not.toBeInTheDocument()
    })
  })

  test('process xong nhưng tải trang kết quả đầu lỗi: vẫn sang bước Kết quả, "Thử lại" chỉ tải lại trang, không chạy lại process', async () => {
    mockSaveTransformations(saved)
    mockSaveValidations(saved)
    const process = mockProcess(processed)
    mockResult(() => problemResponse(503, 'INTERNAL_ERROR', 'Service unavailable.'), resultPage)
    const user = userEvent.setup()
    render(<App />)
    await openRulesStep(user)
    const log = recordRequests()

    await user.click(runButton())

    const alert = await screen.findByRole('alert')
    expect(screen.getByRole('heading', RESULT_HEADING)).toBeInTheDocument()
    await user.click(within(alert).getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('table', { name: 'Dòng lỗi' })).toBeInTheDocument()
    expect(process.calls()).toBe(1)
    expect(log.slice(-2)).toEqual(['GET result?view=invalid&page=0&size=50', 'GET result?view=invalid&page=0&size=50'])
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

    await addStep(user, 'Họ tên', 'trim')

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
