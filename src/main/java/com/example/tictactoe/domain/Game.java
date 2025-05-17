package com.example.tictactoe.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {
    @Id
    @Column(name = "game_id")
    private UUID gameId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "game_status")
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "next_turn", columnDefinition = "symbol")
    private Symbol nextTurn;

    private OffsetDateTime createdAt;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "winner", columnDefinition = "symbol")
    private Symbol winner;

    @OneToMany(mappedBy = "game", fetch = FetchType.LAZY)
    private List<Player> players = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Move> moves = new ArrayList<>();

    public String getResult() {
        if (status != GameStatus.FINISHED) {
            return null;
        }
        return winner != null
                ? winner.name()
                : "DRAW";
    }
}
