package com.example.tictactoe.dto;

import com.example.tictactoe.domain.Symbol;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlayerDto(
        UUID playerId,
        String name,
        Symbol symbol,
        OffsetDateTime joinedAt
) {}
