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

**1. Discovery endpoint:**
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

**6. Attempt to delete a room with sensors:**
```bash
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301
```

**7. Attempt to post a reading to a maintenance sensor:**
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5.0}'
```

---

## Conceptual Report

### Part 1 — Q1: JAX-RS Resource Lifecycle

By default, JAX-RS creates a new instance of each resource class for every incoming HTTP request. This behaviour is known as per-request scope. Because a fresh object is created per request, any data stored as an instance variable inside a resource class would be lost as soon as the request finishes. This is the reason a separate DataStore class was introduced using the singleton pattern. The DataStore holds all rooms, sensors and readings inside ConcurrentHashMap structures, which are shared safely across all incoming requests. Without this approach, two simultaneous requests could overwrite each other's data or read incomplete state, leading to race conditions and data loss.

### Part 1 — Q2: HATEOAS

HATEOAS stands for Hypermedia As The Engine Of Application State. The idea is that an API response should not just return data but should also tell the client where it can go next by including relevant links. In our discovery endpoint for example, the response includes the paths to the rooms and sensors collections so a client can navigate the entire API starting from a single entry point without needing to read any external documentation. This is beneficial for client developers because it reduces the coupling between the client and the server. If a path changes in a future version of the API, clients that follow the links dynamically will adapt automatically rather than breaking.

### Part 2 — Q3: IDs vs Full Objects

Returning only IDs in a list response is more bandwidth efficient but it forces the client to make an additional GET request for every single item it needs details about. This pattern is sometimes called the N+1 problem because fetching a list of 100 rooms would actually require 101 separate HTTP requests. On the other hand, returning full objects in one response uses more bandwidth upfront but gives the client everything it needs in a single round trip. For this API, returning full room objects was chosen because room data is compact and clients typically need all the fields at once when rendering a list view.

### Part 2 — Q4: DELETE Idempotency

Yes, the DELETE operation is idempotent in this implementation. Idempotency means that sending the same request multiple times results in the same server state as sending it just once. If a client sends DELETE to a room that exists, the room is removed and a 204 response is returned. If the same DELETE request is sent again, the room is already gone so a 404 is returned instead. The server state does not change between the first and second call because the room is absent in both cases. The response code is different but the underlying state of the data remains consistent, which satisfies the idempotency requirement defined in the HTTP specification.

### Part 3 — Q5: @Consumes Mismatch

When a client sends a request with a Content-Type header that does not match what the endpoint declares, JAX-RS automatically rejects the request before the method body is ever executed. In this case the POST endpoints declare @Consumes(MediaType.APPLICATION_JSON), so if a client sends text/plain or application/xml, Jersey will respond with a 415 Unsupported Media Type status code immediately. This is handled entirely at the framework level which means the application code never needs to manually inspect or validate the content type. It also protects the API from receiving data in unexpected formats that Jackson would not be able to deserialise correctly.

### Part 3 — Q6: Query Param vs Path Param

Query parameters and path parameters serve different purposes in REST design. Path parameters are used to identify a specific resource, for example /sensors/TEMP-001 points to one particular sensor. Query parameters are used to modify or filter a collection request without changing the identity of the resource being accessed. Using a path like /sensors/type/CO2 would imply that type/CO2 is itself a resource, which is semantically incorrect. The query parameter approach with /sensors?type=CO2 makes it clear that the client is requesting the sensors collection and applying a filter to the results. Query parameters are also far more flexible because multiple filters can be combined in a single request such as ?type=CO2&status=ACTIVE without requiring any changes to the API routing structure.

### Part 4 — Q7: Sub-Resource Locator Pattern

The sub-resource locator pattern allows a resource class to delegate handling of a nested path to a completely separate class. In this API, SensorResource handles everything under /sensors and uses a locator method to hand off any request to /sensors/{id}/readings to the dedicated SensorReadingResource class. This separation keeps each class focused on a single area of responsibility. SensorReadingResource only needs to know about readings and does not need to be aware of rooms or sensors beyond the sensor ID it receives. In larger APIs this pattern prevents individual resource classes from growing into unmanageable files with hundreds of methods. It also makes the code easier to maintain and test because each class can be worked on independently.

### Part 5 — Q8: HTTP 422 vs 404

A 404 Not Found response communicates that the URL the client requested does not exist on the server. A 422 Unprocessable Entity response communicates something different: the URL was valid and the server understood the request, but the data inside the request body contains a logical error. When a client tries to register a sensor with a roomId that does not exist in the system, the endpoint /api/v1/sensors is perfectly valid. The problem is not the URL but the content of the JSON payload. Returning a 404 in this situation would confuse the client into thinking the sensors endpoint itself is missing. A 422 is the more accurate choice because it tells the client that the structure of the request was correct but the referenced resource inside the body could not be resolved.

### Part 5 — Q9: Stack Trace Security Risk

Exposing raw Java stack traces in API error responses is a serious security risk. A stack trace reveals the internal package and class names of the application, which gives an attacker a detailed map of the codebase. It also exposes the names and versions of third party libraries being used, making it straightforward to look up known vulnerabilities for those specific versions in public databases. In some cases stack traces can also leak file system paths, SQL query fragments or configuration details that should never be visible outside the server. The GlobalExceptionMapper in this API addresses this by catching all unexpected exceptions, logging the full details internally where only developers can access them, and returning a simple generic 500 message to the client with no internal information included.

### Part 5 — Q10: Filters vs Manual Logging

Using a JAX-RS filter for logging is a much cleaner approach than inserting log statements manually into every resource method. Logging is a cross-cutting concern, meaning it applies equally to all parts of the application regardless of what business logic they perform. If logging were added manually to each method, the same boilerplate code would need to be duplicated dozens of times across the codebase. Any new endpoint added in the future would need to remember to include the same logging code, and any change to the log format would require updating every single method individually. A single LoggingFilter class registered with Jersey intercepts every request and response automatically without any changes needed in the resource classes themselves. This keeps the resource classes clean and focused entirely on their own logic.