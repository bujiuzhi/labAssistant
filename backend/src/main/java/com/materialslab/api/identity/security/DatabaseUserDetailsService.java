package com.materialslab.api.identity.security;

import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.mapper.IdentityMapper;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 从组织用户表加载 Spring Security 主体。 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final IdentityMapper identityMapper;

    public DatabaseUserDetailsService(IdentityMapper identityMapper) { this.identityMapper = identityMapper; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = identityMapper.findActiveByUsername(username);
        if (account == null) throw new UsernameNotFoundException("用户名或密码错误");
        return principal(account);
    }

    /** 按账户主键重新加载有效主体，用于每个已登录请求校验会话版本和最新授权。 */
    public UserPrincipal loadActiveById(java.util.UUID userId) {
        UserAccount account = identityMapper.findById(userId);
        if (account == null || !"active".equals(account.status())) {
            throw new UsernameNotFoundException("登录状态已失效");
        }
        return principal(account);
    }

    private UserPrincipal principal(UserAccount account) {
        List<SimpleGrantedAuthority> authorities = identityMapper.listPermissionCodes(account.id()).stream()
                .map(permission -> new SimpleGrantedAuthority("PERM_" + permission)).toList();
        return new UserPrincipal(account, authorities);
    }
}
