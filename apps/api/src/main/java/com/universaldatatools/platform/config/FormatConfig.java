package com.universaldatatools.platform.config;

import com.universaldatatools.core.format.csv.CsvSourceParser;
import com.universaldatatools.core.format.xlsx.XlsxLimits;
import com.universaldatatools.core.format.xlsx.XlsxSourceParser;
import com.universaldatatools.core.format.xlsx.XlsxZipGuard;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The file-format readers of core.format as beans; core itself stays free of Spring (core-01 TD1). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(XlsxLimitsProperties.class)
public class FormatConfig {

    @Bean
    XlsxLimits xlsxLimits(XlsxLimitsProperties properties) {
        return properties.toLimits();
    }

    @Bean
    XlsxZipGuard xlsxZipGuard(XlsxLimits limits) {
        return new XlsxZipGuard(limits);
    }

    @Bean
    CsvSourceParser csvSourceParser() {
        return new CsvSourceParser();
    }

    @Bean
    XlsxSourceParser xlsxSourceParser(XlsxZipGuard guard) {
        return new XlsxSourceParser(guard);
    }
}
