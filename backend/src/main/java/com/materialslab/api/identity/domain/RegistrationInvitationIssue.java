package com.materialslab.api.identity.domain;

import java.time.OffsetDateTime;

/** 新邀请码的单次签发结果；邀请码明文不得持久化、记录日志或再次查询。 */
public record RegistrationInvitationIssue(String invitationCode, String roleCode, OffsetDateTime expiresAt) { }
