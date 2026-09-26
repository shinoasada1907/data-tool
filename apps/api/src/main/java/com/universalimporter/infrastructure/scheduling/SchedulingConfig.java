package com.universalimporter.infrastructure.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs; they all live in this package (BE-F11 D3). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
