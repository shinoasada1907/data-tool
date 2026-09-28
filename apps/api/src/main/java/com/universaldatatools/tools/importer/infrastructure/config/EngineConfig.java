package com.universaldatatools.tools.importer.infrastructure.config;

import com.universaldatatools.core.format.csv.CsvTableReader;
import com.universaldatatools.core.format.xlsx.XlsxTableReader;
import com.universaldatatools.core.transform.TransformationEngine;
import com.universaldatatools.core.transform.TransformationRegistry;
import com.universaldatatools.core.validate.ValidationRegistry;
import com.universaldatatools.tools.importer.domain.importsession.SourceParser;
import com.universaldatatools.tools.importer.domain.mapping.MappingStrategies;
import com.universaldatatools.tools.importer.domain.pipeline.DefaultImportPipeline;
import com.universaldatatools.tools.importer.domain.pipeline.ImportPipeline;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfigValidator;
import com.universaldatatools.tools.importer.domain.validation.FieldValidator;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfigValidator;
import com.universaldatatools.tools.importer.infrastructure.parser.TableSourceParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The domain engines as beans; the domain itself stays free of Spring (design D1, BE-F06 T7). */
@Configuration(proxyBeanMethods = false)
public class EngineConfig {

    /** The importer reads CSV and XLSX only, with the V0.1 rules (core-02 IO10). */
    @Bean
    SourceParser csvSourceParser(CsvTableReader reader) {
        return new TableSourceParser(reader);
    }

    @Bean
    SourceParser xlsxSourceParser(XlsxTableReader reader) {
        return new TableSourceParser(reader);
    }

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

    @Bean
    ImportPipeline importPipeline(TransformationEngine transformationEngine, FieldValidator fieldValidator) {
        return new DefaultImportPipeline(MappingStrategies.standard(), transformationEngine, fieldValidator);
    }
}
