package com.smartcampus.exception.mapper;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger logger = Logger.getLogger(
        GlobalExceptionMapper.class.getName()
    );

    @Override
    public Response toResponse(Throwable e) {
        // Log the full error server-side but never expose it to the client
        logger.severe("Unexpected error: " + e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "An unexpected error occurred. Please try again later."
                ))
                .build();
    }
}