/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.bankb.tracing;

import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Component
public class TracingResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, 
                      ContainerResponseContext responseContext) throws IOException {
        
        // Add trace ID to response headers
        String traceId = getCurrentTraceId();
        if (traceId != null && !traceId.equals("no-trace")) {
            responseContext.getHeaders().add("X-Trace-Id", traceId);
        }

        // Clean up the scope
        Scope scope = (Scope) requestContext.getProperty("otel.scope");
        if (scope != null) {
            scope.close();
        }
    }

    private String getCurrentTraceId() {
        try {
            return io.opentelemetry.api.trace.Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            return "no-trace";
        }
    }
}
