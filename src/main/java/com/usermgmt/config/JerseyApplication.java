package com.usermgmt.config;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Jersey JAX-RS Application Configuration.
 * Registers REST resource packages and Jackson JSON provider.
 */
public class JerseyApplication extends ResourceConfig {

    public JerseyApplication() {
        //telling jersey to scan this package for REST resource classes
        packages("com.usermgmt.rest");
        register(JacksonFeature.class);
    }
}
