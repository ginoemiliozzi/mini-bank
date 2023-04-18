# Mini bank app

Example app using Akka and Cassandra from [Rock the JVM](https://www.youtube.com/@rockthejvm)

### Architecture

Entry point is the HTTP server built with Akka HTTP

    POST /bank
      Payload: bank account creation request
      Response:
        201 Created
          Location: /bank/:uuid
        400 Bad Request

    GET /bank/:uuid
      Response:
       200 Ok
        Bank acc as JSON
       404 Not Found

    PUT /bank/:uuid
      Payload: (currency, amount) as JSON
      Response:
        200 Ok
          Account updated as JSON
        400 Bad request
        404 Not Found

Persistent Akka actors are used to handle the logic, messages are persisted to Cassandra database.

There is a main Bank actor that spawns children actors for each bank account.

Payloads validation is performed using Cats.

### Usage
- `docker compose up` to prepare Cassandra service
- Run the app
- Interact with the app via the HTTP API