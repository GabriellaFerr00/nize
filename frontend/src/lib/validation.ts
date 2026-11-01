/** Light client-side checks for instant feedback; the back-end remains the authority. */
export const isEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())

export type PasswordProblem = 'required' | 'tooShort' | 'tooLong' | null

export function passwordProblem(value: string): PasswordProblem {
  if (value.length === 0) return 'required'
  if (value.length < 8) return 'tooShort'
  if (value.length > 72) return 'tooLong'
  return null
}
