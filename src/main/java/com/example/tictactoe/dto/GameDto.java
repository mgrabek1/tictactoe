package com.example.tictactoe.dto;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Symbol;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GameDto(
        UUID gameId,
        GameStatus status,
        Symbol nextTurn,
        OffsetDateTime createdAt,
        List<PlayerDto> players,
        List<MoveDto> moves,
        String result,
        Symbol winner
) {}
