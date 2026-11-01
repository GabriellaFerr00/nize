import type { TFunction } from 'i18next'
import { ApiError } from '../api/client'

const KNOWN_CODES = [
  'INVALID_CREDENTIALS',
  'EMAIL_ALREADY_REGISTERED',
  'INVALID_RESET_TOKEN',
  'VALIDATION_ERROR',
  'UNKNOWN_YEAR',
]

/** Translates an error thrown by the API client into a user-facing message. */
export function apiErrorMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError && KNOWN_CODES.includes(error.code)) return t(`errors.${error.code}`)
  if (error instanceof TypeError) return t('errors.NETWORK')
  return t('errors.UNKNOWN')
}
