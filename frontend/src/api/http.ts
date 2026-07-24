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

export function getProblemDetail(error: unknown): ProblemDetail | null {
  if (!(error instanceof AxiosError)) {
    return null;
  }
  return (error.response?.data as ProblemDetail | undefined) ?? null;
}
