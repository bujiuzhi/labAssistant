/** 项目写请求幂等键生成回归测试。 */

import assert from "node:assert/strict";
import test from "node:test";

import { createIdempotencyKey } from "../src/api/idempotency.ts";

test("安全上下文优先使用浏览器原生 UUID", () => {
  const expected = "123e4567-e89b-42d3-a456-426614174000";
  const cryptoProvider = {
    randomUUID: () => expected,
  } as Crypto;

  assert.equal(createIdempotencyKey(cryptoProvider), expected);
});

test("局域网 HTTP 环境使用随机字节生成 v4 UUID", () => {
  const cryptoProvider = {
    getRandomValues: (bytes: Uint8Array) => {
      bytes.set([
        0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa,
        0xbb, 0xcc, 0xdd, 0xee, 0xff,
      ]);
      return bytes;
    },
  } as unknown as Crypto;

  const key = createIdempotencyKey(cryptoProvider);

  assert.equal(key, "00112233-4455-4677-8899-aabbccddeeff");
  assert.match(key, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
