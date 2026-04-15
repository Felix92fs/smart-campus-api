package com.smartcampus.resource;

import com.smartcampus.model.Room;
import com.smartcampus.store.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private final DataStore store = DataStore.getInstance();

    // GET /api/v1/rooms — list all rooms
    @GET
    public Response getAllRooms() {
        List<Room> rooms = new ArrayList<>(store.getRooms().values());
        return Response.ok(rooms).build();
    }

    // GET /api/v1/rooms/{roomId} — get a single room
    @GET
    @Path("/{roomId}")
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRoom(roomId);
        if (room == null) {
            Map<String, String> error = Map.of(
                "error", "Room not found",
                "id", roomId
            );
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(error)
                           .build();
        }
        return Response.ok(room).build();
    }

    // POST /api/v1/rooms — create a new room
    @POST
    public Response createRoom(Room room) {
        if (room.getId() == null || room.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(Map.of("error", "Room id is required"))
                           .build();
        }
        if (store.getRoom(room.getId()) != null) {
            return Response.status(Response.Status.CONFLICT)
                           .entity(Map.of("error", "Room with this id already exists"))
                           .build();
        }
        store.addRoom(room);
        return Response.status(Response.Status.CREATED)
                       .entity(room)
                       .build();
    }

    // DELETE /api/v1/rooms/{roomId} — delete a room
    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRoom(roomId);

        // 404 if room doesn't exist
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(Map.of("error", "Room not found", "id", roomId))
                           .build();
        }

        // 409 if room still has sensors — safety logic from the brief
        if (!room.getSensorIds().isEmpty()) {
            return Response.status(Response.Status.CONFLICT)
                           .entity(Map.of(
                               "error", "Cannot delete room with active sensors",
                               "sensorCount", String.valueOf(room.getSensorIds().size()),
                               "sensors", room.getSensorIds().toString()
                           ))
                           .build();
        }

        store.deleteRoom(roomId);
        return Response.noContent().build(); // 204 — success, nothing to return
    }
}