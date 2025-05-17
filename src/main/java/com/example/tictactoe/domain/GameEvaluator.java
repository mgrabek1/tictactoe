package com.example.tictactoe.domain;

import java.util.List;
import java.util.Optional;

public interface GameEvaluator {
    Optional<Symbol> evaluate(List<Move> moves);
}
