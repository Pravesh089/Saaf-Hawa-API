package com.saafhawa.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 3 document metadata, published at /docs (NFR-4). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI saafHawaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Saaf Hawa API")
                .version("v1")
                .description("Historical + real-time Indian air-quality data with first-class QC flags. "
                        + "Data: CPCB via data.gov.in. Send your key as the X-API-Key header.")
                .contact(new Contact().name("Saaf Hawa").url("https://github.com/pravesh089/saaf-hawa-api"))
                .license(new License().name("MIT")));
    }
}
