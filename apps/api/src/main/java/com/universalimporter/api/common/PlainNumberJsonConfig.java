package com.universalimporter.api.common;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;

/**
 * Responses write decimals as plain numbers (design F09-D5): {@code 0.0000001}, never {@code 1E-7}. Only the API
 * mapper is affected; the result store keeps its own.
 */
@Configuration(proxyBeanMethods = false)
public class PlainNumberJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer plainBigDecimals() {
        return builder -> builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }
}
