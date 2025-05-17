package com.example.tictactoe.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MoveDto(
        UUID moveId,
        UUID playerId,
        int row,
        int col,
        OffsetDateTime movedAt
) {}