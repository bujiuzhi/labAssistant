package com.materialslab.bootstrap;

import com.materialslab.api.identity.security.PasswordEncodingConfiguration;
import com.materialslab.api.common.storage.ObjectStorageConfiguration;
import com.materialslab.api.common.storage.ObjectStorageProperties;
import com.materialslab.api.common.storage.ObjectStorageService;
import java.util.Arrays;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import software.amazon.awssdk.services.s3.S3Client;

/** 显式调用的无 Web 生产身份初始化入口；位于 API 扫描包外，正常启动绝不自动执行。 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import({PasswordEncodingConfiguration.class, ObjectStorageConfiguration.class})
public class ProductionBootstrapApplication {
    /** 只从环境与 Secret 文件接收配置；失败退出非零，不支持命令行覆盖生产 Profile。 */
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("生产初始化不接受命令行参数，请配置环境变量与 Secret 文件");
        }
        SpringApplication application = new SpringApplication(ProductionBootstrapApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("prod");
        try (var context = application.run()) {
            // ApplicationRunner 已在 run 返回前完成；关闭连接池以结束一次性任务。
        }
    }

    @Bean
    ObjectStorageService objectStorageService(ObjectStorageProperties properties, ObjectProvider<S3Client> clientProvider) {
        return new ObjectStorageService(properties, clientProvider);
    }

    @Bean
    ApplicationRunner productionIdentityBootstrap(DataSource dataSource, PasswordEncoder passwordEncoder, Environment environment,
            ObjectStorageService objectStorageService) {
        if (!Arrays.asList(environment.getActiveProfiles()).equals(java.util.List.of("prod"))) {
            throw new IllegalStateException("生产初始化只允许 prod Profile");
        }
        BootstrapSettings settings = BootstrapSettings.from(environment);
        String encodedPassword = passwordEncoder.encode(BootstrapSettings.readPassword(environment));
        var initializer = new ProductionIdentityInitializer(new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        return args -> {
            objectStorageService.verifyReady();
            initializer.initialize(dataSource, args, settings, encodedPassword);
        };
    }
}
