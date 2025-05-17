package com.example.tictactoe.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WebSocketIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("tictactoe")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    private final WebSocketClient client = new ReactorNettyWebSocketClient();

    private String wsUri() {
        return "ws://localhost:" + port + "/ws/games";
    }

    @Test
    void createJoinGetFlow_overWebSocket() throws Exception {
        var responses = new CopyOnWriteArrayList<String>();

        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage("{\"action\":\"create\"}"))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        assertThat(responses).hasSize(1);
        JsonNode created = mapper.readTree(responses.getFirst());
        assertThat(created.get("type").asText()).isEqualTo("created");
        String gameId = created.get("gameId").asText();

        responses.clear();
        String joinJson = String.format("{\"action\":\"join\",\"gameId\":\"%s\",\"name\":\"Alice\"}", gameId);
        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage(joinJson))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        JsonNode joined = mapper.readTree(responses.getFirst());
        assertThat(joined.get("type").asText()).isEqualTo("joined");
        assertThat(joined.get("player").get("symbol").asText()).isEqualTo("X");

        responses.clear();
        String getJson = String.format("{\"action\":\"get\",\"gameId\":\"%s\"}", gameId);
        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage(getJson))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        JsonNode state = mapper.readTree(responses.getFirst());
        assertThat(state.get("type").asText()).isEqualTo("state");
        assertThat(state.get("game").get("status").asText()).isEqualTo("WAITING");
    }

    @Test
    void invalidJson_yieldsErrorMessage() {
        var responses = new CopyOnWriteArrayList<String>();

        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage("not json"))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst()).contains("\"error\":\"Invalid JSON\"");
    }

    @Test
    void moveAndReceiveUpdate_overWebSocket() throws Exception {
        // Collect each phase's response separately
        var responses = new CopyOnWriteArrayList<String>();

        // 1) CREATE
        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage("{\"action\":\"create\"}"))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        // Extract gameId
        JsonNode created = mapper.readTree(responses.getFirst());
        String gameId = created.get("gameId").asText();
        responses.clear();

        // 2) JOIN Alice
        String joinAlice = String.format("{\"action\":\"join\",\"gameId\":\"%s\",\"name\":\"Alice\"}", gameId);
        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage(joinAlice))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        JsonNode aliceJoined = mapper.readTree(responses.getFirst());
        String aliceId = aliceJoined.get("player").get("playerId").asText();
        responses.clear();

        // 3) JOIN Bob
        String joinBob = String.format("{\"action\":\"join\",\"gameId\":\"%s\",\"name\":\"Bob\"}", gameId);
        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage(joinBob))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        responses.clear();

        // 4) FIRST MOVE by Alice
        String moveJson = String.format("{\"action\":\"move\",\"gameId\":\"%s\",\"move\":{\"playerId\":\"%s\",\"row\":0,\"col\":0}}", gameId, aliceId);
        client.execute(URI.create(wsUri()), session -> session.send(Flux.just(session.textMessage(moveJson))).thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(responses::add)).then()).block(Duration.ofSeconds(5));

        assertThat(responses).hasSize(1);
        JsonNode update = mapper.readTree(responses.getFirst());
        assertThat(update.get("type").asText()).isEqualTo("update");
        // After one move, moves array should contain exactly one entry
        assertThat(update.get("game").get("moves")).hasSize(1);
    }
}
