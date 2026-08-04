package com.materialslab.api.identity.service;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.mapper.IdentityMapper;
import com.materialslab.api.identity.security.DatabaseUserDetailsService;
import com.materialslab.api.identity.security.UserPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 处理登录、会话及账户选项的应用服务。 */
@Service
public class IdentityService {
    private final DatabaseUserDetailsService userDetailsService;
    private final IdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(DatabaseUserDetailsService userDetailsService, IdentityMapper identityMapper, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 按用户名和密码建立认证主体。 */
    public Authentication authenticate(String username, String password) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        try { return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password)); }
        catch (Exception error) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "用户名或密码错误"); }
    }

    /** 序列化前端所需的当前会话。 */
    public Map<String, Object> session(UserPrincipal principal) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", principal.userId()); user.put("username", principal.getUsername());
        user.put("display_name", principal.account().displayName()); user.put("organization_id", principal.organizationId());
        user.put("is_super_admin", principal.isSuperAdmin());
        user.put("permissions", principal.isSuperAdmin() ? List.of("*") : identityMapper.listPermissionCodes(principal.userId()));
        user.put("role_codes", identityMapper.listRoleCodes(principal.userId()));
        return user;
    }

    /** 返回当前登录用户可选择的负责人和成员。 */
    public List<Map<String, Object>> visibleUserOptions(UserPrincipal principal) {
        return identityMapper.listVisibleActiveUsers(principal.organizationId()).stream().map(account -> Map.<String, Object>of(
                "id", account.id(), "username", account.username(), "display_name", account.displayName())).toList();
    }

    /** 取得经过类型校验的当前主体。 */
    public static UserPrincipal currentPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) return userPrincipal;
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "authentication_required", "登录状态已失效");
    }
}
