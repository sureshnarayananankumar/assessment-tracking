import { isAxiosError } from 'axios';
import type { ApiErrorBody } from '../api/types';

export function errorCode(error: unknown): string | undefined {
  if (isAxiosError<ApiErrorBody>(error)) return error.response?.data?.code;
  return undefined;
}

export function errorStatus(error: unknown): number | undefined {
  if (isAxiosError(error)) return error.response?.status;
  return undefined;
}

export function fieldErrors(error: unknown): Record<string, string> {
  if (isAxiosError<ApiErrorBody>(error)) return error.response?.data?.fieldErrors ?? {};
  return {};
}

/** Human readable message for any API / network error. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (isAxiosError<ApiErrorBody>(error)) {
    if (!error.response) return 'Cannot reach the server. Check your connection and try again.';
    const body = error.response.data;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length) {
      return Object.entries(body.fieldErrors)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join(' • ');
    }
    if (body?.message) return body.message;
    if (error.response.status === 403) return 'You do not have permission to perform this action.';
    if (error.response.status === 404) return 'The requested item was not found.';
  }
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
