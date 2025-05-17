package com.example.tictactoe.service;

import com.example.tictactoe.domain.*;
import com.example.tictactoe.dto.*;
import com.example.tictactoe.exception.*;
import com.example.tictactoe.mapper.GameMapper;
import com.example.tictactoe.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final MoveRepository moveRepo;
    private final GameMapper mapper;
    private final GameEvaluator evaluator;

    @CacheEvict(value = "games", allEntries = true)
    @Transactional
    public UUID createGame() {
        UUID newGameId = UUID.randomUUID();
        log.info("Starting creation of new game with id={}", newGameId);
        var game = Game.builder()
                .gameId(newGameId)
                .status(GameStatus.WAITING)
                .nextTurn(Symbol.X)
                .createdAt(OffsetDateTime.now())
                .build();
        gameRepo.save(game);
        log.debug("Game persisted: {}", game);
        return newGameId;
    }

    @CacheEvict(value = "games", allEntries = true)
    @Transactional
    public PlayerDto joinGame(UUID gameId, String name) {
        log.info("Attempting to join game id={} as player='{}'", gameId, name);
        var game = gameRepo.findById(gameId)
                .orElseThrow(() -> {
                    log.error("Game not found: id={}", gameId);
                    return new GameNotFoundException(gameId);
                });

        if (game.getPlayers().size() >= 2) {
            log.warn("Game id={} already has two players", gameId);
            throw new InvalidMoveException("Game already has two players");
        }

        var symbol = game.getPlayers().isEmpty() ? Symbol.X : Symbol.O;
        log.debug("Assigned symbol={} to new player in game id={}", symbol, gameId);

        var player = Player.builder()
                .playerId(UUID.randomUUID())
                .name(name)
                .symbol(symbol)
                .joinedAt(OffsetDateTime.now())
                .game(game)
                .build();

        playerRepo.save(player);
        log.debug("Player persisted: {}", player);

        game.getPlayers().add(player);
        if (game.getPlayers().size() == 2) {
            game.setStatus(GameStatus.IN_PROGRESS);
            gameRepo.save(game);
            log.info("Game id={} status changed to IN_PROGRESS", gameId);
        }

        PlayerDto dto = mapper.toDto(player);
        log.info("Player joined successfully: gameId={}, playerId={}, symbol={}", gameId, dto.playerId(), dto.symbol());
        return dto;
    }

    @CacheEvict(value = "games", allEntries = true)
    @Transactional
    public GameDto makeMove(UUID gameId, MoveRequest req) {
        log.info("Player {} is attempting move on game id={} at row={}, col={}", req.playerId(), gameId, req.row(), req.col());
        var game = gameRepo.findById(gameId)
                .orElseThrow(() -> {
                    log.error("Game not found for move: id={}", gameId);
                    return new GameNotFoundException(gameId);
                });
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            log.warn("Invalid move: game id={} is not in progress but {}", gameId, game.getStatus());
            throw new InvalidMoveException("Game is not in progress");
        }
        var player = playerRepo.findById(req.playerId())
                .orElseThrow(() -> {
                    log.error("Player not found: id={}", req.playerId());
                    return new InvalidMoveException("Player not found");
                });
        if (!player.getGame().getGameId().equals(gameId)) {
            log.warn("Invalid move: player {} does not belong to game {}", req.playerId(), gameId);
            throw new InvalidMoveException("Player not in this game");
        }
        if (player.getSymbol() != game.getNextTurn()) {
            log.warn("Invalid move: not {}'s turn in game id={}", player.getSymbol(), gameId);
            throw new InvalidMoveException("Not your turn");
        }
        for (var m : game.getMoves()) {
            if (m.getRow() == req.row() && m.getCol() == req.col()) {
                log.warn("Invalid move: cell {}x{} already occupied in game id={}", req.row(), req.col(), gameId);
                throw new InvalidMoveException("Cell already occupied");
            }
        }

        var move = Move.builder()
                .moveId(UUID.randomUUID())
                .game(game)
                .player(player)
                .row(req.row())
                .col(req.col())
                .movedAt(OffsetDateTime.now())
                .build();

        try {
            moveRepo.save(move);
            log.debug("Move persisted: {}", move);
        } catch (DataIntegrityViolationException ex) {
            log.error("Database integrity violation on move: {}", move, ex);
            throw new InvalidMoveException("Cell already occupied");
        }

        game.getMoves().add(move);
        Optional<Symbol> winnerOpt = evaluator.evaluate(game.getMoves());
        if (winnerOpt.isPresent()) {
            game.setStatus(GameStatus.FINISHED);
            game.setWinner(winnerOpt.get());
            game.setNextTurn(null);
            log.info("Game id={} finished, winner={}", gameId, winnerOpt.get());
        } else if (game.getMoves().size() == 9) {
            game.setStatus(GameStatus.FINISHED);
            game.setWinner(null);
            game.setNextTurn(null);
            log.info("Game id={} finished with a draw", gameId);
        } else {
            Symbol next = game.getNextTurn() == Symbol.X ? Symbol.O : Symbol.X;
            game.setNextTurn(next);
            log.debug("Next turn set to={} for game id={}", next, gameId);
        }

        gameRepo.save(game);
        log.debug("Game state updated: {}", game);
        GameDto dto = mapper.toDto(game);
        log.info("Move processed successfully for game id={}, returning DTO", gameId);
        return dto;
    }

    @Cacheable("games")
    @Transactional(readOnly = true)
    public GameDto getGame(UUID gameId) {
        log.debug("Fetching game state for id={}", gameId);
        var game = gameRepo.findById(gameId)
                .orElseThrow(() -> {
                    log.error("Game not found on getGame: id={}", gameId);
                    return new GameNotFoundException(gameId);
                });
        GameDto dto = mapper.toDto(game);
        log.debug("Returning GameDto: {}", dto);
        return dto;
    }

    @Cacheable(value = "games", key = "#status")
    @Transactional(readOnly = true)
    public List<GameDto> listGames(GameStatus status) {
        log.debug("Listing games with status={}", status);
        var games = gameRepo.findByStatus(status);
        List<GameDto> list = games.stream().map(mapper::toDto).toList();
        log.debug("Found {} games with status={}", list.size(), status);
        return list;
    }
}
