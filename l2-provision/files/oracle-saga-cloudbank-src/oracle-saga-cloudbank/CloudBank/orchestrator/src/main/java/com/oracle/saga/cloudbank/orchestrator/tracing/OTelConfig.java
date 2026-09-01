/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.orchestrator.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class OTelConfig {

        @Value("${tracing.zipkin.url:http://localhost:9411/api/v2/spans}")
        private String zipkinUrl;

        @Value("${spring.application.name:orchestrator}")
        private String serviceName;

        @Value("${tracing.enabled:true}")
        private boolean tracingEnabled;

        private OpenTelemetrySdk openTelemetry;

        private static final Logger logger = LoggerFactory.getLogger(OTelConfig.class);

        @PostConstruct
        public void initOpenTelemetry() {
                if (!tracingEnabled) {
                        logger.info("Tracing is disabled");
                        return;
                }

                logger.info("Initializing OpenTelemetry with Zipkin URL: {}", zipkinUrl);

                try {
                        // Simple resource with just service name
                        Resource resource = Resource.create(
                                        Attributes.of(AttributeKey.stringKey("service.name"), serviceName));

                        // Zipkin exporter
                        ZipkinSpanExporter zipkinExporter = ZipkinSpanExporter.builder()
                                        .setEndpoint(zipkinUrl)
                                        .build();

                        // Simple tracer provider
                        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                                        .setResource(resource)
                                        .addSpanProcessor(BatchSpanProcessor.builder(zipkinExporter).build())
                                        .build();

                        // Build and register
                        this.openTelemetry = OpenTelemetrySdk.builder()
                                        .setTracerProvider(tracerProvider)
                                        .setPropagators(ContextPropagators.create(
                                                        W3CTraceContextPropagator.getInstance()))
                                        .buildAndRegisterGlobal();

                        logger.info("OpenTelemetry initialized successfully for service: {}", serviceName);

                } catch (Exception e) {
                        logger.error("Failed to initialize OpenTelemetry: {}", e.getMessage());
                }
        }

        @Bean
        public OpenTelemetry openTelemetry() {
                if (!tracingEnabled) {
                        return OpenTelemetry.noop();
                }
                return GlobalOpenTelemetry.get();
        }

        @PreDestroy
        public void cleanup() {
                if (openTelemetry != null) {
                        try {
                                openTelemetry.close();
                                logger.info("OpenTelemetry closed successfully");
                        } catch (Exception e) {
                                logger.error("Error closing OpenTelemetry: {}", e.getMessage());
                        }
                }
        }
}