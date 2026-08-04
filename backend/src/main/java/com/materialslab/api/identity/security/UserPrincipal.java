package com.materialslab.api.identity.security;

import com.materialslab.api.identity.domain.UserAccount;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 写入 HTTP Session 的认证主体。 */
public record UserPrincipal(UserAccount account, Collection<? extends GrantedAuthority> authorities) implements UserDetails {
    public UUID userId() { return account.id(); }
    public UUID organizationId() { return account.organizationId(); }
    public boolean isSuperAdmin() { return account.superAdmin(); }
    @Override public String getPassword() { return account.password(); }
    @Override public String getUsername() { return account.username(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isEnabled() { return "active".equals(account.status()); }
}
