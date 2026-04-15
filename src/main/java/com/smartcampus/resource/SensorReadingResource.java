package com.smartcampus.resource;

import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.store.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final DataStore store = DataStore.getInstance();
    private final String sensorId;

    // Constructor receives the sensorId from the parent SensorResource
    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    // GET /api/v1/sensors/{sensorId}/readings
    @GET
    public Response getReadings() {
        Sensor sensor = store.getSensor(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(Map.of("error", "Sensor not found", "id", sensorId))
                           .build();
        }
        List<SensorReading> readings = store.getReadings(sensorId);
        return Response.ok(readings).build();
    }

    // POST /api/v1/sensors/{sensorId}/readings
    @POST
    public Response addReading(SensorReading reading) {
        Sensor sensor = store.getSensor(sensorId);

        // 404 if sensor doesn't exist
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(Map.of("error", "Sensor not found", "id", sensorId))
                           .build();
        }

        // 403 if sensor is in MAINTENANCE — can't accept readings
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            return Response.status(Response.Status.FORBIDDEN)
                           .entity(Map.of(
                               "error", "Sensor is under maintenance and cannot accept readings",
                               "sensorId", sensorId,
                               "status", sensor.getStatus()
                           ))
                           .build();
        }

        // Create a proper reading with timestamp and UUID
        SensorReading newReading = new SensorReading(reading.getValue());

        // Save the reading
        store.addReading(sensorId, newReading);

        // KEY SIDE EFFECT: update the sensor's currentValue
        sensor.setCurrentValue(reading.getValue());

        return Response.status(Response.Status.CREATED)
                       .entity(newReading)
                       .build();
    }
}