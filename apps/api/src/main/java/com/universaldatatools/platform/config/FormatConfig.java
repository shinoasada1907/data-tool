package com.universaldatatools.platform.config;

import com.universaldatatools.core.format.csv.CsvTableReader;
import com.universaldatatools.core.format.csv.CsvTableWriter;
import com.universaldatatools.core.format.json.JsonTableReader;
import com.universaldatatools.core.format.json.JsonTableWriter;
import com.universaldatatools.core.format.xlsx.XlsxLimits;
import com.universaldatatools.core.format.xlsx.XlsxTableReader;
import com.universaldatatools.core.format.xlsx.XlsxTableWriter;
import com.universaldatatools.core.format.xlsx.XlsxZipGuard;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The readers and writers of core.format as beans; core itself stays free of Spring (core-01 TD1). */
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
    CsvTableReader csvTableReader() {
        return new CsvTableReader();
    }

    @Bean
    XlsxTableReader xlsxTableReader(XlsxZipGuard guard) {
        return new XlsxTableReader(guard);
    }

    @Bean
    JsonTableReader jsonTableReader() {
        return new JsonTableReader();
    }

    @Bean
    CsvTableWriter csvTableWriter() {
        return new CsvTableWriter();
    }

    @Bean
    JsonTableWriter jsonTableWriter() {
        return new JsonTableWriter();
    }

    @Bean
    XlsxTableWriter xlsxTableWriter() {
        return new XlsxTableWriter();
    }
}
