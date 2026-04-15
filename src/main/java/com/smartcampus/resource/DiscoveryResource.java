package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {

        // Links map — tells clients where each resource lives
        Map<String, String> links = new HashMap<>();
        links.put("rooms",   "/api/v1/rooms");
        links.put("sensors", "/api/v1/sensors");

        // Full response body
        Map<String, Object> response = new HashMap<>();
        response.put("name",        "Smart Campus API");
        response.put("version",     "1.0");
        response.put("description", "Sensor and Room Management API");
        response.put("contact",     "admin@smartcampus.ac.uk");
        response.put("resources",   links);

        return Response.ok(response).build();
    }
}