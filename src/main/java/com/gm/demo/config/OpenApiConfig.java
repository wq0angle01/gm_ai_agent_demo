package com.gm.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 文档元信息；接口清单由 springdoc 扫描 @RestController 生成。 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gmOpenApi() {
        return new OpenAPI().info(new Info()
                .title("GM 运营管理后台 API")
                .version("A0")
                .description("Phase A：不含 LLM 调用的传统运营后台"));
    }
}
