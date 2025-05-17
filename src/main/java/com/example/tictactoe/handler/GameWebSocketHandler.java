package com.example.tictactoe.handler;

import com.example.tictactoe.dto.MoveRequest;
import com.example.tictactoe.service.GameService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {
    private final GameService service;
    private final ObjectMapper mapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(text -> process(text)
                        .flatMap(responseJson -> session.send(
                                Mono.just(session.textMessage(responseJson)))
                        )
                )
                .doOnError(e -> log.error("WebSocket handling error", e))
                .then();
    }

    private Mono<String> process(String payload) {
        JsonNode node;
        try {
            node = mapper.readTree(payload);
        } catch (JsonProcessingException e) {
            return Mono.just(error("Invalid JSON"));
        }

        String action = node.path("action").asText("");
        return switch (action) {
            case "create" -> handleCreate();
            case "join" -> handleJoin(node);
            case "move" -> handleMove(node);
            case "get" -> handleGet(node);
            default -> Mono.just(error("Unknown action"));
        };
    }

    private Mono<String> handleCreate() {
        return Mono.fromCallable(service::createGame)
                .map(id -> Map.of("type", "created", "gameId", id))
                .map(this::toJsonSafe);
    }

    private Mono<String> handleJoin(JsonNode node) {
        try {
            UUID gameId = UUID.fromString(node.path("gameId").asText());
            String name = node.path("name").asText();
            var dto = service.joinGame(gameId, name);
            return Mono.just(toJsonSafe(Map.of("type", "joined", "player", dto)));
        } catch (IllegalArgumentException ex) {
            return Mono.just(error("Invalid gameId"));
        }
    }

    private Mono<String> handleMove(JsonNode node) {
        try {
            UUID gameId = UUID.fromString(node.path("gameId").asText());
            MoveRequest req = mapper.treeToValue(node.path("move"), MoveRequest.class);
            var gameDto = service.makeMove(gameId, req);
            return Mono.just(toJsonSafe(Map.of("type", "update", "game", gameDto)));
        } catch (Exception ex) {
            return Mono.just(error("Bad move request"));
        }
    }

    private Mono<String> handleGet(JsonNode node) {
        try {
            UUID gameId = UUID.fromString(node.path("gameId").asText());
            var dto = service.getGame(gameId);
            return Mono.just(toJsonSafe(Map.of("type", "state", "game", dto)));
        } catch (IllegalArgumentException ex) {
            return Mono.just(error("Invalid gameId"));
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed for {}", obj, e);
            return error("Server error");
        }
    }

    private String error(String msg) {
        return String.format("{\"error\":\"%s\"}", msg);
    }
}
