package com.asee.estadisticas.config;

import com.asee.estadisticas.websocket.EstadisticasWebSocket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;

@Configuration(proxyBeanMethods=false)
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {
    private final EstadisticasWebSocket handler;
    private final String origins;
    public WebSocketConfig(EstadisticasWebSocket handler,@Value("${app.allowed-origins}") String origins) { this.handler=handler; this.origins=origins; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler,"/ws").setAllowedOriginPatterns(origins.split(","));
    }
}
