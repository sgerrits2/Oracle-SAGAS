/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.banka.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Component
public class TracingRequestFilter implements ContainerRequestFilter {

    @Autowired
    private OpenTelemetry openTelemetry;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Extract trace context from incoming headers
        Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), requestContext, new JerseyHeaderGetter());

        // Make the extracted context current and store scope for cleanup
        Scope scope = extractedContext.makeCurrent();
        requestContext.setProperty("otel.scope", scope);
        
        // Store saga ID if present
        String sagaId = requestContext.getHeaderString("X-Saga-Id");
        if (sagaId != null) {
            requestContext.setProperty("saga.id", sagaId);
        }
    }

    private static class JerseyHeaderGetter implements TextMapGetter<ContainerRequestContext> {
        @Override
        public Iterable<String> keys(ContainerRequestContext carrier) {
            return carrier.getHeaders().keySet();
        }

        @Override
        public String get(ContainerRequestContext carrier, String key) {
            return carrier.getHeaderString(key);
        }
    }
}
