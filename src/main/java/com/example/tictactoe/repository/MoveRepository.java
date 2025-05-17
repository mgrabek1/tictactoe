package com.example.tictactoe.repository;

import com.example.tictactoe.domain.Move;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MoveRepository extends JpaRepository<Move, UUID> {
}
