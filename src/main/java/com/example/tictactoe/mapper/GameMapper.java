package com.example.tictactoe.mapper;

import com.example.tictactoe.domain.*;
import com.example.tictactoe.dto.*;
import org.mapstruct.*;

import java.util.*;

@Mapper(componentModel = "spring", imports = {ArrayList.class})
public interface GameMapper {
    @Mapping(target = "winner", source = "winner")
    @Mapping(target = "result", expression = "java(game.getResult())")
    GameDto toDto(Game game);

    PlayerDto toDto(Player player);

    MoveDto toDto(Move move);
}
