## Tic-Tac-Toe Reactive WebSocket API Documentation

This document provides a comprehensive overview of how our implementation meets all functional and non-functional requirements for the “Tik Tac Toe – Challenge” and justifies the choice of technologies and architectural patterns. It highlights clean, maintainable code practices and senior-level design decisions.

---

### 1. Functional Requirements and Their Fulfillment

1. **Two Players with Unique Symbols X and O**

    * Clients connect via WebSocket and send `create` and `join` messages. The first player automatically receives symbol **X**, the second **O**. Attempts to join beyond two players raise an `InvalidMoveException("Game full")`.
    * **Implementation**: Handled in `GameService.joinGame()` and the WebSocket handler.

2. **3×3 Board and Win Conditions**

    * After each move, the game service evaluates whether a player has aligned three of their symbols in a row, column, or diagonal on the standard 3×3 grid. This is done by comparing the set of positions occupied by each player against a predefined collection of the eight possible winning patterns. If any pattern is fully covered by one player’s positions, that player is immediately declared the winner and the game ends.

3. **Draw when No Moves Left**3. **Draw when No Moves Left**

    * When the total number of moves reaches 9 without a winner, `evaluateGame()` also marks the game as **FINISHED**.

4. **Persistent Game State**

    * **Spring Data JPA** with PostgreSQL, schema migrations managed by **Flyway** (`V1__init.sql`), ensure every move, player, and game record is persisted.

5. **Concurrency Control**

    * The `Game` entity uses **optimistic locking** (`@Version`) and service methods are annotated with `@Transactional` to prevent race conditions in concurrent updates.

6. **Input Validation**

    * `validateMove()` method enforces:

        * Game is in **IN\_PROGRESS** state
        * Player belongs to the specified game
        * It is the player’s turn
        * Target cell is empty
    * Violations result in `InvalidMoveException` with clear error messages.

7. **Real-Time, Low-Latency Interaction**

    * **Spring WebFlux** + **WebSocket** (Netty) avoid HTTP handshake overhead, enabling sub-millisecond messaging.
    * **Caffeine Cache** stores game state in memory (cache-aside), reducing DB reads to the initial load and final persistence, achieving microsecond-level access.
    * The JVM is configured with **ZGC** (`-XX:+UseZGC -XX:+ZUncommit`) to guarantee very short GC pauses (<1ms).

8. **Logging and Traceability**

    * A custom `RequestIdFilter` injects a unique `requestId` into the MDC for every session. SLF4J/Logback logs key events (`game created`, `player joined`, `move played`, errors) tagged with this `requestId` to facilitate distributed tracing and debugging.

---

### 2. Architecture and Layered Design

* **Transport Layer**: WebSocket endpoints defined in `GameWebSocketHandler`, enabling reactive, bidirectional streams.
* **Service Layer**: `GameService` contains transactional business logic, validation, state evaluation, and cache synchronization.
* **Domain Layer**: JPA entities (`Game`, `Player`, `Move`) and enums (`GameStatus`, `Symbol`) model the game.
* **Persistence Layer**: Spring Data JPA repositories and Flyway migrations manage DB interactions and schema versioning.
* **Cache Layer**: Spring Cache abstraction with **Caffeine**, configured to expire entries after one hour and hold up to 10,000 games.
* **Mapping**: MapStruct automatically maps between entities and DTO records, eliminating boilerplate.
* **Exception Handling**: `GlobalExceptionHandler` converts domain exceptions into appropriate HTTP or WebSocket error messages.
* **Configuration**: `application.yml`, `.env`, and environment variables centralize all adjustable parameters.

---

### 3. Technology Stack and Rationale

| Technology              | Purpose & Benefits                                                                                         |
| ----------------------- | ---------------------------------------------------------------------------------------------------------- |
| Spring WebFlux + Netty  | Non-blocking, event-driven server supporting thousands of open WebSocket connections with minimal threads. |
| WebSocket               | Persistent, low-overhead connection for real-time gameplay.                                                |
| Caffeine Cache          | Ultra-fast in-memory store for game state, reducing latency and DB load.                                   |
| PostgreSQL + Flyway     | Robust relational storage with controlled, repeatable schema migrations.                                   |
| Spring Data JPA         | Simplifies CRUD and query operations with built-in transaction management and optimistic locking.          |
| MapStruct               | Compile-time DTO ↔ Entity mapping, eliminating runtime reflection and boilerplate code.                    |
| Jakarta Bean Validation | Declarative request validation using `@NotNull`, `@Min`, `@Max`, ensuring clean input handling.            |
| SLF4J + Logback + MDC   | Structured logging with contextual `requestId`, enabling easy tracing of multi-step operations.            |
| ZGC                     | Low-latency garbage collector that avoids long stop-the-world pauses, critical for real-time applications. |

---

### 4. Clean Code and Best Practices

* **Declarative Win Logic**: using `Position` record and `WINNING_COMBINATIONS` improves readability vs. raw array indices.
* **SOLID Principles**: clear separation of responsibilities between handler, service, and repository layers.
* **DRY**: centralized validation logic in `validateMove()`, reusable mapping logic via MapStruct.
* **KISS**: minimal classes, expressive DTO records, and straightforward flow of control.
* **Transaction Management**: `@Transactional` annotations ensure data consistency and rollback on errors.
* **Cache-Aside Pattern**: ensures cache and DB are synchronized, with explicit cache updates in `createGame()` and `makeMove()`.
* **Reactive Streams**: leveraging Reactor’s `Flux` and `Mono` for non-blocking backpressure-aware data flow.

---

## 5. Build, Deployment & Testing

This section covers how to build the TicTacToe application, run it using Docker Compose, and execute the test suite.

### 5.1 Building the Application

1. **Prerequisites**

   * JDK 21 installed
   * Maven 3.8+ installed

2. **Compile & Package**

   ```bash
   mvn clean package
   ```

   * Runs all unit and integration tests
   * Produces `target/tictactoe-1.0-SNAPSHOT.jar`

3. **Skip Tests (optional)**

   ```bash
   mvn clean package -DskipTests
   ```

### 5.2 Running with Docker Compose

The repository includes a `docker-compose.yml` that brings up the application and PostgreSQL.


Start services:

```bash
docker-compose up --build
```

* Application available at `http://localhost:8080`.
* WebSocket endpoint at `ws://localhost:8080/ws/games`.

### 5.3 Testing

1. **Unit Tests**

   ```bash
   mvn test
   ```

   * Runs `GameServiceTest`, verifying service logic with mocks.

2. **Integration Tests**

   ```bash
   mvn verify
   ```

   * Uses Testcontainers to spin up PostgreSQL and runs `GameIntegrationTest`, `ExtendedGameIntegrationTest`, and `WebSocketIntegrationTest`.

---
