package com.sentinel.aml.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sentinelAmlOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Sentinel AML API")
                .description("Real-time money laundering detection platform for MeridianTrust Bank: "
                        + "ingests customer/account/transaction data, runs it through a configurable "
                        + "rule-based detection engine (structuring, rapid-movement, high-risk jurisdiction), "
                        + "and exposes risk-scored, explainable alerts with a case-disposition workflow.")
                .version("v1")
                .contact(new Contact().name("Sentinel AML engineering squad")));
    }
}
