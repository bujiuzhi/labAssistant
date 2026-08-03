/**
 * 生成兼容局域网 HTTP 访问环境的写请求幂等键。
 *
 * `crypto.randomUUID()` 仅在安全上下文可用，而 `crypto.getRandomValues()`
 * 可在普通 HTTP 页面使用，因此优先使用前者并以 RFC 4122 v4 UUID 作为回退。
 *
 * @param cryptoProvider 浏览器加密能力，测试时可注入受控实现
 * @returns 长度符合后端约束的唯一幂等键
 */
export function createIdempotencyKey(
  cryptoProvider: Crypto | undefined = globalThis.crypto,
): string {
  if (typeof cryptoProvider?.randomUUID === "function") {
    return cryptoProvider.randomUUID();
  }

  if (typeof cryptoProvider?.getRandomValues !== "function") {
    return [
      "fallback",
      Date.now().toString(36),
      Math.random().toString(36).slice(2),
      Math.random().toString(36).slice(2),
    ].join("-");
  }

  const bytes = cryptoProvider.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hexadecimal = Array.from(bytes, (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");

  return [
    hexadecimal.slice(0, 8),
    hexadecimal.slice(8, 12),
    hexadecimal.slice(12, 16),
    hexadecimal.slice(16, 20),
    hexadecimal.slice(20),
  ].join("-");
}
