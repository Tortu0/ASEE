package com.asee.estadisticas.websocket;

import com.asee.estadisticas.exception.ApiException;
import com.asee.estadisticas.model.EstadisticasPartidoDocumento;
import com.asee.estadisticas.service.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EstadisticasWebSocket extends TextWebSocketHandler {
    private static final class Client {
        final WebSocketSession session;
        final Map<UUID,Long> subscriptions=new HashMap<>();
        long pong=System.currentTimeMillis(), window=System.currentTimeMillis()/1000;
        int messages;
        Client(WebSocketSession session) { this.session=new ConcurrentWebSocketSessionDecorator(session,5000,65536); }
    }
    private final Map<String,Client> clients=new ConcurrentHashMap<>();
    private final EstadisticasService service;
    private final JsonMapper json;
    private final int maxConnections,maxSubscriptions,maxMessages;
    public EstadisticasWebSocket(EstadisticasService service,JsonMapper json,
            @Value("${app.websocket.max-connections:100}") int maxConnections,
            @Value("${app.websocket.max-subscriptions:5}") int maxSubscriptions,
            @Value("${app.websocket.max-messages-per-second:20}") int maxMessages) {
        this.service=service; this.json=json; this.maxConnections=maxConnections; this.maxSubscriptions=maxSubscriptions; this.maxMessages=maxMessages;
    }
    @Override public synchronized void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (clients.size()>=maxConnections) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        session.setTextMessageSizeLimit(4096);
        clients.put(session.getId(),new Client(session));
    }
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
        Client client=clients.get(session.getId()); if (client==null) return;
        synchronized(client) {
            long window=System.currentTimeMillis()/1000;
            if (window!=client.window) { client.window=window; client.messages=0; }
            if (++client.messages>maxMessages) { client.session.close(CloseStatus.POLICY_VIOLATION); return; }
            try {
                var value=json.readTree(message.getPayload());
                if (!value.isObject() || !value.path("tipo").isTextual()) throw new IllegalArgumentException();
                String tipo=value.path("tipo").asString();
                if (tipo.equals("pong")) { client.pong=System.currentTimeMillis(); return; }
                if (!Set.of("suscribir","cancelar_suscripcion").contains(tipo)) throw new IllegalArgumentException();
                UUID id=UUID.fromString(value.path("partidoId").asString());
                if (tipo.equals("cancelar_suscripcion")) { client.subscriptions.remove(id); return; }
                if (!client.subscriptions.containsKey(id) && client.subscriptions.size()>=maxSubscriptions) {
                    error(client,"limite_suscripciones","Se ha alcanzado el limite de partidos"); return;
                }
                var estado=service.estado(id);
                client.subscriptions.put(id,-1L);
                snapshot(client,estado);
            } catch (ApiException ex) {
                error(client,ex.status()==404?"partido_no_encontrado":"servicio_no_disponible",ex.getMessage());
            } catch (IllegalArgumentException ex) { error(client,"mensaje_invalido","Usa suscribir, cancelar_suscripcion o pong con un partidoId UUID valido"); }
            catch (RuntimeException ex) { error(client,"mensaje_invalido","No se puede procesar el mensaje"); }
        }
    }
    private void snapshot(Client client,EstadisticasPartidoDocumento estado) throws IOException {
        Long version=client.subscriptions.get(estado.partidoId());
        if (version==null || version>=estado.versionPublica()) return;
        client.session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("tipo","estado","partidoId",estado.partidoId(),
                "version",estado.versionPublica(),"datos",Calculos.partido(estado)))));
        client.subscriptions.put(estado.partidoId(),estado.versionPublica());
    }
    private void error(Client client,String code,String message) throws IOException {
        client.session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("tipo","error","codigo",code,"mensaje",message))));
    }
    @EventListener public void changed(PartidoActualizado event) { broadcast(event.partidoId()); }
    private void broadcast(UUID id) {
        // Un fallo de entrega nunca convierte una escritura confirmada en un error HTTP.
        try {
            var estado=service.persistido(id); if (estado.isEmpty()) return;
            for (Client client:clients.values()) synchronized(client) {
                try { snapshot(client,estado.get()); }
                catch (IOException | RuntimeException ex) { disconnect(client); }
            }
        } catch (RuntimeException ex) { LoggerFactory.getLogger(getClass()).warn("Actualizacion WebSocket pendiente; se reintentara en el sondeo"); }
    }
    @Scheduled(fixedDelayString="${app.websocket.poll-ms:1000}") public void poll() {
        Set<UUID> ids=new HashSet<>();
        clients.values().forEach(c -> { synchronized(c) { ids.addAll(c.subscriptions.keySet()); } });
        ids.forEach(this::broadcast);
    }
    @Scheduled(fixedDelayString="${app.websocket.heartbeat-ms:30000}") public void heartbeat() {
        for (Client client:clients.values()) synchronized(client) {
            try {
                if (System.currentTimeMillis()-client.pong>90000) { disconnect(client); continue; }
                client.session.sendMessage(new TextMessage("{\"tipo\":\"heartbeat\"}"));
            } catch (IOException | RuntimeException ex) { disconnect(client); }
        }
    }
    private void disconnect(Client client) {
        clients.remove(client.session.getId());
        try { client.session.close(CloseStatus.NORMAL); } catch (IOException ignored) {}
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { clients.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session,Throwable error) throws Exception {
        clients.remove(session.getId()); session.close(CloseStatus.SERVER_ERROR);
    }
}
