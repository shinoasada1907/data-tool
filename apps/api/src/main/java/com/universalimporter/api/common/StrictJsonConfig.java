package com.universalimporter.api.common;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Request bodies must use the JSON type of each property: a value of another type is 400 {@code REQUEST_INVALID}
 * (spec: target-schema, "Request schema sai cấu trúc"). By default Jackson quietly converts {@code "true"} to
 * true, {@code "1"} and {@code 1.9} to 1, and {@code 123} to "123". Only the API mapper is affected;
 * storage keeps its own mappers.
 */
@Configuration(proxyBeanMethods = false)
public class StrictJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer strictScalarTypes() {
        return builder -> builder
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                // Strings are not "scalars" to the feature above, so numbers and booleans need their own rule.
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
