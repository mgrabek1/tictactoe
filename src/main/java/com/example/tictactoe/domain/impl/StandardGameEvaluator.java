package com.example.tictactoe.domain.impl;

import com.example.tictactoe.domain.GameEvaluator;
import com.example.tictactoe.domain.Move;
import com.example.tictactoe.domain.Symbol;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StandardGameEvaluator implements GameEvaluator {

    private static final record Position(int row, int col) {}

    private static final Set<Set<Position>> WINNING_COMBINATIONS = Set.of(
            Set.of(new Position(0,0), new Position(0,1), new Position(0,2)),
            Set.of(new Position(1,0), new Position(1,1), new Position(1,2)),
            Set.of(new Position(2,0), new Position(2,1), new Position(2,2)),
            Set.of(new Position(0,0), new Position(1,0), new Position(2,0)),
            Set.of(new Position(0,1), new Position(1,1), new Position(2,1)),
            Set.of(new Position(0,2), new Position(1,2), new Position(2,2)),
            Set.of(new Position(0,0), new Position(1,1), new Position(2,2)),
            Set.of(new Position(0,2), new Position(1,1), new Position(2,0))
    );

    @Override
    public Optional<Symbol> evaluate(List<Move> moves) {
        Map<Symbol, Set<Position>> positionsBySymbol = moves.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getPlayer().getSymbol(),
                        Collectors.mapping(
                                m -> new Position(m.getRow(), m.getCol()),
                                Collectors.toSet()
                        )
                ));

        Set<Position> xPositions = positionsBySymbol.getOrDefault(Symbol.X, Collections.emptySet());
        Set<Position> oPositions = positionsBySymbol.getOrDefault(Symbol.O, Collections.emptySet());

        for (Set<Position> combo : WINNING_COMBINATIONS) {
            if (xPositions.containsAll(combo)) {
                return Optional.of(Symbol.X);
            }
        }
        for (Set<Position> combo : WINNING_COMBINATIONS) {
            if (oPositions.containsAll(combo)) {
                return Optional.of(Symbol.O);
            }
        }
        return Optional.empty();
    }
}
