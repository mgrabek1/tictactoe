package com.example.tictactoe.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String GENERIC_SERVER_MESSAGE =
            "An unexpected error occurred. Please contact support.";

    @ExceptionHandler(GameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(GameNotFoundException ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("ErrorId {}: Game not found", errorId, ex);
        return new ErrorResponse(ex.getMessage(), errorId);
    }

    @ExceptionHandler(InvalidMoveException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidMove(InvalidMoveException ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("ErrorId {}: Invalid move attempted", errorId, ex);
        return new ErrorResponse(ex.getMessage(), errorId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String errorId = UUID.randomUUID().toString();
        String message = "Invalid parameter: " + ex.getName();
        log.error("ErrorId {}: {}", errorId, message, ex);
        return new ErrorResponse(message, errorId);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAllOthers(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("ErrorId {}: Unhandled server error", errorId, ex);
        return new ErrorResponse(GENERIC_SERVER_MESSAGE, errorId);
    }
}
