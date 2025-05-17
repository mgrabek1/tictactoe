package com.example.tictactoe.service;

import com.example.tictactoe.domain.*;
import com.example.tictactoe.dto.*;
import com.example.tictactoe.exception.*;
import com.example.tictactoe.mapper.GameMapper;
import com.example.tictactoe.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServiceTest {

    @Mock
    private GameRepository gameRepo;
    @Mock
    private PlayerRepository playerRepo;
    @Mock
    private MoveRepository moveRepo;
    @Mock
    private GameMapper mapper;
    @Mock
    private GameEvaluator evaluator;

    @InjectMocks
    private GameService service;

    private UUID gameId;
    private Game game;
    private Player playerX;
    private Player playerO;
    private MoveRequest reqX;
    private MoveRequest reqO;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        game = Game.builder()
                .gameId(gameId)
                .status(GameStatus.IN_PROGRESS)
                .nextTurn(Symbol.X)
                .createdAt(OffsetDateTime.now())
                .players(new ArrayList<>())
                .moves(new ArrayList<>())
                .build();

        playerX = Player.builder()
                .playerId(UUID.randomUUID())
                .symbol(Symbol.X)
                .game(game)
                .build();
        playerO = Player.builder()
                .playerId(UUID.randomUUID())
                .symbol(Symbol.O)
                .game(game)
                .build();

        reqX = new MoveRequest(playerX.getPlayerId(), 0, 0);
        reqO = new MoveRequest(playerO.getPlayerId(), 1, 1);

        when(gameRepo.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(playerRepo.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));
        when(moveRepo.save(any(Move.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createGame_shouldInitializeWaitingGame() {
        UUID newId = service.createGame();

        assertThat(newId).isNotNull();

        ArgumentCaptor<Game> capt = ArgumentCaptor.forClass(Game.class);
        verify(gameRepo).save(capt.capture());

        Game saved = capt.getValue();
        assertThat(saved.getGameId()).isEqualTo(newId);
        assertThat(saved.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(saved.getNextTurn()).isEqualTo(Symbol.X);
    }

    @Test
    void joinGame_firstPlayer_assignsSymbolX_andKeepsWaiting() {
        when(gameRepo.findById(gameId)).thenReturn(Optional.of(
                Game.builder().gameId(gameId).players(new ArrayList<>()).build()
        ));
        PlayerDto expectedDto = new PlayerDto(UUID.randomUUID(), "Alice", Symbol.X, OffsetDateTime.now());
        when(mapper.toDto(any(Player.class))).thenReturn(expectedDto);

        PlayerDto dto = service.joinGame(gameId, "Alice");

        assertThat(dto).isEqualTo(expectedDto);
        verify(playerRepo).save(any(Player.class));

        ArgumentCaptor<Game> gameCap = ArgumentCaptor.forClass(Game.class);
        verify(gameRepo, never()).save(gameCap.capture());
    }

    @Test
    void joinGame_secondPlayer_assignsSymbolO_andStartsGame() {
        var players = new ArrayList<Player>();
        players.add(playerX);
        when(gameRepo.findById(gameId)).thenReturn(Optional.of(
                Game.builder().gameId(gameId).players(players).status(GameStatus.WAITING).build()
        ));
        PlayerDto expectedDto = new PlayerDto(UUID.randomUUID(), "Bob", Symbol.O, OffsetDateTime.now());
        when(mapper.toDto(any(Player.class))).thenReturn(expectedDto);

        PlayerDto dto = service.joinGame(gameId, "Bob");

        assertThat(dto).isEqualTo(expectedDto);
        ArgumentCaptor<Game> gameCap = ArgumentCaptor.forClass(Game.class);
        verify(gameRepo).save(gameCap.capture());
        assertThat(gameCap.getValue().getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    void joinGame_whenFull_throwsInvalidMove() {
        var players = List.of(playerX, playerO);
        when(gameRepo.findById(gameId)).thenReturn(Optional.of(
                Game.builder().gameId(gameId).players(new ArrayList<>(players)).build()
        ));

        assertThatThrownBy(() -> service.joinGame(gameId, "Charlie"))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("already has two players");
    }

    @Test
    void makeMove_whenCellOccupied_throwsInvalidMove() {
        Move occupied = Move.builder().row(0).col(0).player(playerX).game(game).build();
        game.getMoves().add(occupied);
        when(gameRepo.findById(gameId)).thenReturn(Optional.of(game));
        when(playerRepo.findById(playerX.getPlayerId())).thenReturn(Optional.of(playerX));
        MoveRequest dup = new MoveRequest(playerX.getPlayerId(), 0, 0);

        assertThatThrownBy(() -> service.makeMove(gameId, dup))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessage("Cell already occupied");
    }

    @Test
    void makeMove_onDataIntegrityViolation_throwsInvalidMove() {
        when(gameRepo.findById(gameId)).thenReturn(Optional.of(game));
        when(playerRepo.findById(playerX.getPlayerId())).thenReturn(Optional.of(playerX));
        doThrow(DataIntegrityViolationException.class)
                .when(moveRepo).save(any(Move.class));

        MoveRequest req = new MoveRequest(playerX.getPlayerId(), 2, 2);
        assertThatThrownBy(() -> service.makeMove(gameId, req))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessage("Cell already occupied");
    }

    @Test
    void makeMove_whenWinnerDetected_setsFinishedAndWinner() {
        when(gameRepo.findById(gameId)).thenReturn(Optional.of(game));
        when(playerRepo.findById(playerX.getPlayerId())).thenReturn(Optional.of(playerX));
        when(evaluator.evaluate(anyList())).thenReturn(Optional.of(Symbol.X));
        when(mapper.toDto(any(Game.class))).thenReturn(mock(GameDto.class));

        GameDto dto = service.makeMove(gameId, reqX);

        ArgumentCaptor<Game> cap = ArgumentCaptor.forClass(Game.class);
        verify(gameRepo).save(cap.capture());
        Game saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(saved.getWinner()).isEqualTo(Symbol.X);
        assertThat(saved.getNextTurn()).isNull();
    }

    @Test
    void getGame_returnsMappedDto() {
        GameDto expected = new GameDto(gameId, GameStatus.IN_PROGRESS, Symbol.X,
                OffsetDateTime.now(), List.of(), List.of(), null, null);
        when(gameRepo.findById(gameId)).thenReturn(Optional.of(game));
        when(mapper.toDto(game)).thenReturn(expected);

        GameDto dto = service.getGame(gameId);
        assertThat(dto).isEqualTo(expected);
    }

    @Test
    void listGames_returnsMappedDtos() {
        Game g1 = Game.builder().gameId(UUID.randomUUID()).build();
        Game g2 = Game.builder().gameId(UUID.randomUUID()).build();
        when(gameRepo.findByStatus(GameStatus.WAITING)).thenReturn(List.of(g1, g2));
        when(mapper.toDto(g1)).thenReturn(new GameDto(g1.getGameId(), null, null, null, List.of(), List.of(), null, null));
        when(mapper.toDto(g2)).thenReturn(new GameDto(g2.getGameId(), null, null, null, List.of(), List.of(), null, null));

        var list = service.listGames(GameStatus.WAITING);
        assertThat(list).hasSize(2)
                .extracting(GameDto::gameId)
                .containsExactly(g1.getGameId(), g2.getGameId());
    }
}
