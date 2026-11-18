package com.asee.compras.web;

import com.asee.compras.aplicacion.PedidoServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reintenta periódicamente la publicación de los eventos "pedido creado" que no llegaron al broker. */
@Component
@ConditionalOnProperty(name = "compras.eventos.publicador", havingValue = "true", matchIfMissing = true)
public class PublicadorProgramado {

    private static final Logger log = LoggerFactory.getLogger(PublicadorProgramado.class);
    private final PedidoServicio pedidos;

    public PublicadorProgramado(PedidoServicio pedidos) {
        this.pedidos = pedidos;
    }

    @Scheduled(fixedDelayString = "${compras.eventos.intervalo-ms:15000}", initialDelayString = "${compras.eventos.intervalo-ms:15000}")
    public void publicarPendientes() {
        try {
            int n = pedidos.publicarPendientes();
            if (n > 0) log.info("Publicados {} eventos de pedido pendientes", n);
        } catch (RuntimeException e) {
            log.warn("No se pudieron publicar los eventos pendientes: {}", e.toString());
        }
    }
}
