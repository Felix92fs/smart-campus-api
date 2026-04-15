package com.smartcampus.resource;

import com.smartcampus.model.Sensor;
import com.smartcampus.model.Room;
import com.smartcampus.store.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final DataStore store = DataStore.getInstance();

    // GET /api/v1/sensors — list all sensors, optional ?type= filter
    @GET
    public Response getAllSensors(@QueryParam("type") String type) {
        List<Sensor> sensors = new ArrayList<>(store.getSensors().values());

        // If type query param provided, filter the list
        if (type != null && !type.isBlank()) {
            sensors.removeIf(s -> !s.getType().equalsIgnoreCase(type));
        }

        return Response.ok(sensors).build();
    }

    // GET /api/v1/sensors/{sensorId} — get a single sensor
    @GET
    @Path("/{sensorId}")
    public Response getSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = store.getSensor(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(Map.of("error", "Sensor not found", "id", sensorId))
                           .build();
        }
        return Response.ok(sensor).build();
    }

    // POST /api/v1/sensors — register a new sensor
    @POST
    public Response createSensor(Sensor sensor) {
        // Validate required fields
        if (sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(Map.of("error", "Sensor id is required"))
                           .build();
        }

        // Check sensor doesn't already exist
        if (store.getSensor(sensor.getId()) != null) {
            return Response.status(Response.Status.CONFLICT)
                           .entity(Map.of("error", "Sensor with this id already exists"))
                           .build();
        }

        // KEY VALIDATION: check the roomId actually exists
        // This is the 422 logic from Part 5 — roomId must reference a real room
        if (sensor.getRoomId() == null || store.getRoom(sensor.getRoomId()) == null) {
            return Response.status(422)
                           .entity(Map.of(
                               "error", "Referenced room does not exist",
                               "roomId", sensor.getRoomId() != null ? sensor.getRoomId() : "null"
                           ))
                           .build();
        }

        // Add sensor to store
        store.addSensor(sensor);

        // Link sensor to its room
        Room room = store.getRoom(sensor.getRoomId());
        room.getSensorIds().add(sensor.getId());

        return Response.status(Response.Status.CREATED)
                       .entity(sensor)
                       .build();
    }

    // Sub-resource locator — delegates to SensorReadingResource
    // This handles /api/v1/sensors/{sensorId}/readings
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(
            @PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }
}