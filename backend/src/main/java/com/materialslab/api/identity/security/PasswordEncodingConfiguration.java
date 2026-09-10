package com.materialslab.api.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 为 HTTP 登录与离线首次初始化提供相同的密码编码规则。 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncodingConfiguration {
    /** 返回当前数据库账号使用的 BCrypt 编码器。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
