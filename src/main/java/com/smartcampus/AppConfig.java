package com.smartcampus;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import javax.ws.rs.ApplicationPath;

@ApplicationPath("/api/v1")
public class AppConfig extends ResourceConfig {

    public AppConfig() {
        packages(
            "com.smartcampus.resource",
            "com.smartcampus.exception.mapper",
            "com.smartcampus.filter"
        );
        register(JacksonFeature.class);
    }
}