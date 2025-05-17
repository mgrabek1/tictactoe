package com.example.tictactoe.config;

import com.example.tictactoe.handler.GameWebSocketHandler;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(GameWebSocketHandler handler) {
        return new SimpleUrlHandlerMapping(Map.of("/ws/games", handler), -1);
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
