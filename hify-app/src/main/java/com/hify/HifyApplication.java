package com.hify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hify 启动入口。
 * <p>
 * {@code @SpringBootApplication} 的默认扫描从 {@code com.hify} 包开始，
 * 覆盖所有子模块（{@code com.hify.agent}、{@code com.hify.chat} 等）。
 * <p>
 * MyBatis-Plus 的 {@code @MapperScan("com.hify")} 在 {@code MybatisPlusConfig} 中配置，
 * 此处无需重复声明。
 * <p>
 * 启动方式：
 * <pre>{@code
 * java -jar hify-app/target/hify-app-1.0.0-SNAPSHOT.jar
 * }</pre>
 */
@SpringBootApplication
public class HifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(HifyApplication.class, args);
    }
}
