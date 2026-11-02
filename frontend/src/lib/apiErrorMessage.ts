import type { TFunction } from 'i18next'
import { ApiError } from '../api/client'

const KNOWN_CODES = [
  'INVALID_CREDENTIALS',
  'TOO_MANY_ATTEMPTS',
  'EMAIL_ALREADY_REGISTERED',
  'INVALID_RESET_TOKEN',
  'VALIDATION_ERROR',
  'UNKNOWN_YEAR',
  'CATEGORY_NAME_TAKEN',
  'CARD_NAME_TAKEN',
  'PERSON_NAME_TAKEN',
  'CATEGORY_IN_USE',
  'YEAR_ALREADY_EXISTS',
  'INVALID_YEAR',
  'NOT_FOUND',
  'INVALID_EXPENSE',
  'INVALID_MONTH',
  'DATE_OUT_OF_MONTH',
  'INACTIVE_REFERENCE',
  'REFERENCE_UNAVAILABLE',
]

/** Translates an error thrown by the API client into a user-facing message. */
export function apiErrorMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError && KNOWN_CODES.includes(error.code)) return t(`errors.${error.code}`)
  if (error instanceof TypeError) return t('errors.NETWORK')
  return t('errors.UNKNOWN')
}
