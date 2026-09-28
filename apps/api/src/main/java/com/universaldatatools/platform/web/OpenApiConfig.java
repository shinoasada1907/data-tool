package com.universaldatatools.platform.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title, description and per-tool groups of the generated API documentation (Swagger UI at /swagger-ui.html). Group
 * {@code all} holds every endpoint; each tool adds a group named after it (spec: api-docs).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI universalDataToolsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Universal Data Tools API")
                .version("0.1")
                .description("""
                        Data tools that share one core. The importer takes CSV and XLSX files through a configurable \
                        pipeline: upload, preview, target schema, mapping, transformations, validations, process, \
                        result and export.

                        Every error is `application/problem+json` with a stable `code` (for example \
                        `FILE_UNSUPPORTED`, `SESSION_NOT_FOUND`); field-level problems are listed in `errors`."""));
    }

    @Bean
    GroupedOpenApi allApi() {
        return GroupedOpenApi.builder().group("all").pathsToMatch("/api/**").build();
    }

    @Bean
    GroupedOpenApi converterApi() {
        return GroupedOpenApi.builder().group("converter").pathsToMatch("/api/converter/**").build();
    }

    @Bean
    GroupedOpenApi datasetsApi() {
        return GroupedOpenApi.builder().group("datasets").pathsToMatch("/api/datasets/**").build();
    }

    @Bean
    GroupedOpenApi importerApi() {
        return GroupedOpenApi.builder().group("importer").pathsToMatch("/api/import-sessions/**").build();
    }
}
