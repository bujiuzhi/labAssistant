package com.materialslab.api.identity.security;

import com.materialslab.api.common.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 在单实例 API 内对连续失败的账号登录进行有界限流，避免以请求头识别不可信客户端地址。 */
@Service
public class LoginAttemptGuard {
    private static final int MAX_ENTRIES = 5_000;
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, Attempt> attempts = new LinkedHashMap<>();

    /** 在认证前拒绝处于临时锁定期的账号。 */
    public synchronized void requireAllowed(String username) {
        String key = key(username);
        Attempt attempt = attempts.get(key);
        Instant now = Instant.now();
        if (attempt == null) return;
        if (attempt.lockedUntil().isAfter(now)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "login_temporarily_locked",
                    "登录尝试过于频繁，请 15 分钟后重试");
        }
        if (attempt.windowStartedAt().plus(WINDOW).isBefore(now)) attempts.remove(key);
    }

    /** 成功认证后清除该账号的失败记录。 */
    public synchronized void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    /** 失败认证后记录次数；达到阈值即从本次请求开始进入临时锁定。 */
    public synchronized void recordFailure(String username) {
        String key = key(username);
        Instant now = Instant.now();
        Attempt previous = attempts.get(key);
        Attempt next = previous == null || previous.windowStartedAt().plus(WINDOW).isBefore(now)
                ? new Attempt(1, now, Instant.EPOCH)
                : new Attempt(previous.failures() + 1, previous.windowStartedAt(), previous.lockedUntil());
        if (next.failures() >= MAX_FAILURES) next = new Attempt(next.failures(), next.windowStartedAt(), now.plus(WINDOW));
        attempts.put(key, next);
        while (attempts.size() > MAX_ENTRIES) {
            String oldest = attempts.keySet().iterator().next();
            attempts.remove(oldest);
        }
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempt(int failures, Instant windowStartedAt, Instant lockedUntil) { }
}
