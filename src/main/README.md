# Smart Campus API

A RESTful API for managing campus rooms and sensors, built with JAX-RS (Jersey) and Apache Tomcat 9.

## API Overview

The Smart Campus API provides endpoints to manage:
- **Rooms** — physical spaces on campus
- **Sensors** — devices deployed inside rooms (temperature, CO2, occupancy)
- **Readings** — historical measurement logs per sensor

Base URL: `http://localhost:8080/smart-campus-api/api/v1`

## Prerequisites

- Java JDK 17+
- Apache Maven 3.9+
- Apache Tomcat 9.0

## Build Instructions

1. Clone the repository:
```bash
git clone https://github.com/Felix92fs/smart-campus-api.git
cd smart-campus-api
```

2. Build the WAR file:
```bash
mvn package
```

3. Deploy to Tomcat:
```bash
copy target\smart-campus-api.war C:\tomcat\webapps\
```

4. Start Tomcat:
```bash
startup.bat
```

5. Access the API at:
```
http://localhost:8080/smart-campus-api/api/v1
```

## Sample curl Commands

**1. Discovery endpoint — get API metadata:**
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1
```

**2. Get all rooms:**
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1/rooms
```

**3. Create a new room:**
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"CS-101","name":"Computer Science Lab","capacity":25}'
```

**4. Get sensors filtered by type:**
```bash
curl -X GET "http://localhost:8080/smart-campus-api/api/v1/sensors?type=CO2"
```

**5. Post a reading to a sensor:**
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":24.5}'
```

**6. Attempt to delete a room with sensors (409 Conflict):**
```bash
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301
```

**7. Attempt to post a reading to a maintenance sensor (403 Forbidden):**
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5.0}'
```

---

## Conceptual Report

### Part 1 — Q1: JAX-RS Resource Lifecycle

By default, JAX-RS creates a new instance of each resource class for every incoming HTTP request. This is called per-request scope. While this ensures thread safety for instance variables, it means shared data cannot be stored inside resource classes. This is why we use a separate `DataStore` singleton — a single shared object using `ConcurrentHashMap` that all resource instances access safely. If we stored room or sensor data as instance variables inside `RoomResource`, each request would get its own empty copy and all data would be lost between requests.

### Part 1 — Q2: HATEOAS

HATEOAS (Hypermedia As The Engine Of Application State) means API responses include links to related resources and available actions. For example, our discovery endpoint returns `/api/v1/rooms` and `/api/v1/sensors` so clients can navigate the API without reading documentation. This benefits client developers because they don't need to hardcode URLs — if the API changes its paths, clients just follow the updated links. It makes the API self-describing and reduces coupling between client and server.

### Part 2 — Q3: IDs vs Full Objects

Returning only IDs is bandwidth-efficient but forces clients to make a separate GET request for each room they need details about — this is called the N+1 problem. For example, displaying 100 rooms would require 101 requests. Returning full objects uses more bandwidth in a single response but allows clients to render everything immediately with one request. For our Smart Campus API, returning full objects is the better choice since room objects are small and clients typically need all the data at once.

### Part 2 — Q4: DELETE Idempotency

Yes, DELETE is idempotent in our implementation. Idempotency means making the same request multiple times produces the same server state as making it once. If a client sends `DELETE /rooms/CS-101` and the room is successfully deleted, sending the exact same request again returns `404 Not Found` — the server state remains the same (room is still gone). The response code differs between the first and second call, but the resource state does not change, which satisfies the definition of idempotency.

### Part 3 — Q5: @Consumes Mismatch

JAX-RS will automatically return a `415 Unsupported Media Type` response before our method is even called. The `@Consumes(MediaType.APPLICATION_JSON)` annotation tells Jersey to only route requests with a `Content-Type: application/json` header to that method. If the header says `text/plain` or `application/xml`, Jersey rejects the request at the framework level. This protects our code from receiving malformed data and means we never need to manually check the content type inside our methods.

### Part 3 — Q6: Query Param vs Path Param

Query parameters are semantically designed for filtering, searching and sorting optional criteria, while path parameters identify a specific resource. `/sensors/type/CO2` implies `type/CO2` is a distinct resource that exists, which is misleading. `/sensors?type=CO2` clearly communicates "give me the sensors collection, filtered by CO2". Query parameters are also more flexible — you can combine multiple filters like `?type=CO2&status=ACTIVE` without changing the URL structure. Path-based filtering would require a new endpoint for every combination of filters.

### Part 4 — Q7: Sub-Resource Locator Pattern

The Sub-Resource Locator pattern delegates nested resource handling to a dedicated class rather than cramming all endpoints into one massive controller. In our API, `SensorResource` handles `/sensors` operations and delegates `/sensors/{id}/readings` to `SensorReadingResource`. This keeps each class focused on a single responsibility — `SensorReadingResource` only needs to know about readings, not sensors or rooms. In large APIs with dozens of nested paths, this prevents controller classes from growing to thousands of lines, makes code easier to test in isolation, and allows team members to work on different resource classes simultaneously without conflicts.

### Part 5 — Q8: HTTP 422 vs 404

A `404 Not Found` means the requested URL/resource doesn't exist. A `422 Unprocessable Entity` means the request was understood and the URL was valid, but the content of the request body contains a semantic error. When a client POSTs a sensor with a non-existent `roomId`, the URL `/api/v1/sensors` is perfectly valid — the problem is inside the JSON payload. Returning 404 would mislead the client into thinking the sensors endpoint doesn't exist. A 422 precisely communicates "your request structure is correct but the data inside it references something that doesn't exist."

### Part 5 — Q9: Stack Trace Security Risk

Stack traces leak critical internal information that attackers can exploit. They reveal the exact Java class names and package structure, making it easier to target specific vulnerabilities. They expose library names and versions, allowing attackers to look up known CVEs for those exact versions. They can reveal file system paths, database query fragments, and internal business logic. Our `GlobalExceptionMapper` prevents this by catching all `Throwable` exceptions, logging the full details server-side where only developers can see them, and returning only a generic `500 Internal Server Error` message to the client.

### Part 5 — Q10: Filters vs Manual Logging

Filters implement the cross-cutting concerns principle — logging applies to every endpoint equally and has nothing to do with business logic. Manually adding `Logger.info()` to every method would mean duplicating the same code across dozens of methods, making it easy to forget on new endpoints and hard to change consistently. A single `LoggingFilter` class automatically intercepts every request and response regardless of which resource handles it. If we want to change the log format, we change one file instead of dozens.