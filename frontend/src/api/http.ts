import axios, { AxiosError } from "axios";

import type { ProblemDetail } from "@/types/api";

export const http = axios.create({
  baseURL: "/api/v1",
  timeout: 15_000,
  withCredentials: true,
  xsrfCookieName: "csrftoken",
  xsrfHeaderName: "X-CSRFToken",
  headers: {
    Accept: "application/json",
  },
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (error instanceof AxiosError && error.response?.status === 401 && window.location.pathname !== "/login") {
      const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
    }
    return Promise.reject(error);
  },
);

export function getProblemDetail(error: unknown): ProblemDetail | null {
  if (!(error instanceof AxiosError)) {
    return null;
  }
  return (error.response?.data as ProblemDetail | undefined) ?? null;
}
