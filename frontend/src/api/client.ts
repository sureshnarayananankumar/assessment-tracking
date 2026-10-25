import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { ApiEnvelope, ApiErrorBody, AuthResponse } from './types';

/**
 * Access tokens live only in memory (never localStorage) to limit XSS exposure. The long-lived
 * refresh token is an HttpOnly, SameSite=Strict cookie scoped to /api/auth that JavaScript cannot read.
 */
let accessToken: string | null = null;
type SessionListener = (auth: AuthResponse | null) => void;
const listeners = new Set<SessionListener>();

export const tokenStore = {
  get: () => accessToken,
  set: (token: string | null) => {
    accessToken = token;
  },
  subscribe: (listener: SessionListener) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  emit: (auth: AuthResponse | null) => listeners.forEach((l) => l(auth)),
};

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  withCredentials: true,
  timeout: 30000,
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
});

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return config;
});

let refreshing: Promise<AuthResponse | null> | null = null;

/** Single-flight refresh: concurrent 401s share one refresh request. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshing) {
    refreshing = axios
      .post<ApiEnvelope<AuthResponse>>(`${api.defaults.baseURL}/auth/refresh`, null, {
        withCredentials: true,
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      })
      .then((res) => {
        accessToken = res.data.data.accessToken;
        tokenStore.emit(res.data.data);
        return res.data.data;
      })
      .catch(() => {
        accessToken = null;
        tokenStore.emit(null);
        return null;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

const NO_RETRY = ['/auth/login', '/auth/refresh', '/auth/logout', '/invitations/'];

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const url = original?.url ?? '';
    if (error.response?.status === 401 && original && !original._retried && !NO_RETRY.some((p) => url.includes(p))) {
      original._retried = true;
      const session = await refreshSession();
      if (session) {
        original.headers.set('Authorization', `Bearer ${session.accessToken}`);
        return api(original);
      }
    }
    return Promise.reject(error);
  },
);

/** Unwraps the {success, data} envelope. */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await api.request<ApiEnvelope<T>>(config);
  return res.data.data;
}

export async function requestMessage(config: AxiosRequestConfig): Promise<string | undefined> {
  const res = await api.request<ApiEnvelope<unknown>>(config);
  return res.data.message;
}

export async function downloadBlob(url: string, fallbackName: string): Promise<void> {
  const res = await api.get<Blob>(url, { responseType: 'blob' });
  const disposition = String(res.headers['content-disposition'] ?? '');
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1] ?? match[2]) : fallbackName;
  const href = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
}
