package com.aiwms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI-WMS 智能仓储管理系统 · 启动类
 *
 * <p>@SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 * <p>@MapperScan 指定 MyBatis Mapper 接口所在包，避免每个接口都加 @Mapper
 * <p>@EnableScheduling 开启定时任务（用于「超时释放冻结库存」）
 */
@SpringBootApplication
@MapperScan("com.aiwms.mapper")
@EnableScheduling
public class AiWmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiWmsApplication.class, args);
        System.out.println("""

                ========================================
                  AI-WMS 启动成功
                  接口文档: http://localhost:8080/api
                ========================================
                """);
    }
}
