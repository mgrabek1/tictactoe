package com.example.tictactoe.repository;

import com.example.tictactoe.domain.Game;
import com.example.tictactoe.domain.GameStatus;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {
    List<Game> findByStatus(GameStatus status);
}
