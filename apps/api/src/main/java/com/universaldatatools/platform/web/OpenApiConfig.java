package com.universaldatatools.platform.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description of the generated API documentation (Swagger UI at /swagger-ui.html). */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI universalImporterOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Universal Importer API")
                .version("0.1")
                .description("""
                        Import CSV and XLSX files through a configurable pipeline: upload, preview, target schema, \
                        mapping, transformations, validations, process, result and export.

                        Every error is `application/problem+json` with a stable `code` (for example \
                        `FILE_UNSUPPORTED`, `SESSION_NOT_FOUND`); field-level problems are listed in `errors`."""));
    }
}
