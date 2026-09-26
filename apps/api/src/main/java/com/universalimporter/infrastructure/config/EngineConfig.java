package com.universalimporter.infrastructure.config;

import com.universalimporter.domain.transformation.TransformationConfigValidator;
import com.universalimporter.domain.transformation.TransformationEngine;
import com.universalimporter.domain.transformation.TransformationRegistry;
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
}
