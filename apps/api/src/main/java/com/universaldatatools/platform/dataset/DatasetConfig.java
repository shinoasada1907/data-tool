package com.universaldatatools.platform.dataset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The inspection cache and the dataset settings (core-04 PL5, PL13). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RetentionProperties.class, LimitsProperties.class})
public class DatasetConfig {

    @Bean
    DatasetInspections datasetInspections(@Value("${toolbox.dataset.inspect-cache-size:256}") int capacity) {
        return new DatasetInspections(capacity);
    }
}
