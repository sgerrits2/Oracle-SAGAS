/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.bankb;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;
import com.oracle.saga.cloudbank.bankb.listener.AccessControlResponseFilter;
import com.oracle.saga.cloudbank.bankb.tracing.TracingRequestFilter;
import com.oracle.saga.cloudbank.bankb.tracing.TracingResponseFilter;

@ApplicationPath("/bankb")
@Component
public class JerseyConfig extends ResourceConfig {
    public JerseyConfig(){
        packages("com.oracle.saga.cloudbank.bankb.controller");
        register(AccessControlResponseFilter.class);
        register(OpenApiResource.class);
        register(TracingRequestFilter.class);
        register(TracingResponseFilter.class);
    }
}
