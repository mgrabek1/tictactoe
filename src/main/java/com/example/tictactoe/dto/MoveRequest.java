package com.example.tictactoe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveRequest(
        @NotNull UUID playerId,
        @Min(0) @Max(2) int row,
        @Min(0) @Max(2) int col
) {
}
