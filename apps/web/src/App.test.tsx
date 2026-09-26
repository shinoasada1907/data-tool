import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import App from './App'

test('mở ứng dụng thì vào thẳng công cụ Import dữ liệu', () => {
  render(<App />)

  expect(screen.getByRole('heading', { level: 1, name: 'Import dữ liệu' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { level: 2, name: 'Upload file nguồn' })).toBeInTheDocument()
})
