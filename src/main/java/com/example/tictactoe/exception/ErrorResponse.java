package com.example.tictactoe.exception;

public record ErrorResponse(String message, String errorId) {
    public ErrorResponse(String message) {
        this(message, null);
    }
}
