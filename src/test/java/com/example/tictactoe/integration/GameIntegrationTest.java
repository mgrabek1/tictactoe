package com.example.tictactoe.integration;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Symbol;
import com.example.tictactoe.dto.GameDto;
import com.example.tictactoe.dto.MoveRequest;
import com.example.tictactoe.dto.PlayerDto;
import com.example.tictactoe.exception.GameNotFoundException;
import com.example.tictactoe.exception.InvalidMoveException;
import com.example.tictactoe.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class GameIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("tictactoe")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "?stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private GameService gameService;

    @Test
    void joinNonExistingGame_throwsGameNotFound() {
        assertThrows(GameNotFoundException.class, () ->
                gameService.joinGame(UUID.randomUUID(), "Alice")
        );
    }

    @Test
    void joinMoreThanTwoPlayers_throwsInvalidMove() {
        UUID gameId = gameService.createGame();
        gameService.joinGame(gameId, "A");
        gameService.joinGame(gameId, "B");

        assertThrows(InvalidMoveException.class, () ->
                gameService.joinGame(gameId, "C")
        );
    }

    @Test
    void moveBeforeGameStarts_throwsInvalidMove() {
        UUID gameId = gameService.createGame();
        PlayerDto p1 = gameService.joinGame(gameId, "Alice");

        MoveRequest req = new MoveRequest(p1.playerId(), 0, 0);
        assertThrows(InvalidMoveException.class, () ->
                gameService.makeMove(gameId, req)
        );
    }

    @Test
    void moveOutOfTurn_throwsInvalidMove() {
        UUID gameId = gameService.createGame();
        PlayerDto p1 = gameService.joinGame(gameId, "Alice");
        PlayerDto p2 = gameService.joinGame(gameId, "Bob");

        MoveRequest invalid = new MoveRequest(p2.playerId(), 0, 0);
        assertThrows(InvalidMoveException.class, () ->
                gameService.makeMove(gameId, invalid)
        );
    }

    @Test
    void moveCellOccupied_throwsInvalidMove() {
        UUID gameId = gameService.createGame();
        PlayerDto p1 = gameService.joinGame(gameId, "Alice");
        PlayerDto p2 = gameService.joinGame(gameId, "Bob");

        gameService.makeMove(gameId, new MoveRequest(p1.playerId(), 0, 0));
        assertThrows(InvalidMoveException.class, () ->
                gameService.makeMove(gameId, new MoveRequest(p2.playerId(), 0, 0))
        );
    }

    @Test
    void fullGame_drawResultInDraw() {
        UUID gameId = gameService.createGame();
        PlayerDto p1 = gameService.joinGame(gameId, "Alice");
        PlayerDto p2 = gameService.joinGame(gameId, "Bob");

        List<MoveRequest> moves = List.of(
                new MoveRequest(p1.playerId(), 0, 0),
                new MoveRequest(p2.playerId(), 0, 1),
                new MoveRequest(p1.playerId(), 0, 2),
                new MoveRequest(p2.playerId(), 1, 1),
                new MoveRequest(p1.playerId(), 1, 0),
                new MoveRequest(p2.playerId(), 1, 2),
                new MoveRequest(p1.playerId(), 2, 1),
                new MoveRequest(p2.playerId(), 2, 0),
                new MoveRequest(p1.playerId(), 2, 2)
        );
        moves.forEach(m -> gameService.makeMove(gameId, m));

        GameDto result = gameService.getGame(gameId);
        assertThat(result.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(result.winner()).isNull();
        assertThat(result.result()).isEqualTo("DRAW");
    }

    @Test
    void fullGame_oWinsDiagonal() {
        UUID gameId = gameService.createGame();
        PlayerDto p1 = gameService.joinGame(gameId, "Alice");
        PlayerDto p2 = gameService.joinGame(gameId, "Bob");

        List<MoveRequest> moves = List.of(
                new MoveRequest(p1.playerId(), 0, 1), // X
                new MoveRequest(p2.playerId(), 0, 0), // O
                new MoveRequest(p1.playerId(), 1, 0), // X
                new MoveRequest(p2.playerId(), 1, 1), // O
                new MoveRequest(p1.playerId(), 2, 1), // X
                new MoveRequest(p2.playerId(), 2, 2)  // O wins
        );
        moves.forEach(m -> gameService.makeMove(gameId, m));

        GameDto finished = gameService.getGame(gameId);
        assertThat(finished.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(finished.winner()).isEqualTo(Symbol.O);
        assertThat(finished.result()).isEqualTo("O");
    }
}
