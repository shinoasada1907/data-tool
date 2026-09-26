package com.universalimporter.infrastructure.config;

import com.universalimporter.domain.transformation.TransformationConfigValidator;
import com.universalimporter.domain.transformation.TransformationEngine;
import com.universalimporter.domain.transformation.TransformationRegistry;
import com.universalimporter.domain.validation.FieldValidator;
import com.universalimporter.domain.validation.ValidationConfigValidator;
import com.universalimporter.domain.validation.ValidationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The domain engines as beans; the domain itself stays free of Spring (design D1, BE-F06 T7). */
@Configuration(proxyBeanMethods = false)
public class EngineConfig {

    @Bean
    TransformationRegistry transformationRegistry() {
        return TransformationRegistry.standard();
    }

    @Bean
    TransformationEngine transformationEngine(TransformationRegistry registry) {
        return new TransformationEngine(registry);
    }

    @Bean
    TransformationConfigValidator transformationConfigValidator(TransformationRegistry registry) {
        return new TransformationConfigValidator(registry);
    }

    @Bean
    ValidationRegistry validationRegistry() {
        return ValidationRegistry.standard();
    }

    @Bean
    FieldValidator fieldValidator(ValidationRegistry registry) {
        return new FieldValidator(registry);
    }

    @Bean
    ValidationConfigValidator validationConfigValidator() {
        return new ValidationConfigValidator();
    }
}
