/** File giả cho test; `size` ghi đè dung lượng mà không phải cấp phát cả chục MB. */
export function testFile(name: string, { size, type = '' }: { size?: number; type?: string } = {}): File {
  const file = new File(['id,name\n1,An\n'], name, { type })
  if (size !== undefined) Object.defineProperty(file, 'size', { value: size })
  return file
}

export const MB = 1024 * 1024
