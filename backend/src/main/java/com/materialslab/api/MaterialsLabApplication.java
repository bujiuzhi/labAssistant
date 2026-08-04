package com.materialslab.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 材料实验助手 Java 服务启动入口。 */
@MapperScan("com.materialslab.api.**.mapper")
@SpringBootApplication
public class MaterialsLabApplication {
    /** 启动嵌入式 HTTP 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(MaterialsLabApplication.class, args);
    }
}
