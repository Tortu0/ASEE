package com.asee.entradas.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

/**
 * Documento MongoDB que representa una entrada comprada.
 *
 * Cada entrada referencia un asiento concreto y lleva su código QR
 * único para el control de acceso al estadio.
 *
 * El índice único (reservaId, asientoId) es la red de seguridad final: aunque
 * fallara la lógica, MongoDB rechaza dos entradas del mismo asiento en una reserva.
 * (Requiere spring.data.mongodb.auto-index-creation=true.)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "entradas")
@CompoundIndex(name = "uk_reserva_asiento", def = "{'reservaId': 1, 'asientoId': 1}", unique = true)
public class Entrada {

    @Id
    private String entradaId;

    @Indexed
    private String partidoId;

    @Indexed
    private String usuarioId;

    /** ID del asiento asociado. */
    private String asientoId;

    private TipoEntrada tipoEntrada;

    private double precioPagado;

    /** Porcentaje de descuento aplicado (0 si no hay descuento). */
    private double descuentoAplicado;

    private MetodoPago metodoPago;

    /** Código QR único para validación en el acceso. */
    private String codigoQr;

    private EstadoEntrada estado = EstadoEntrada.valida;

    private Instant fechaCompra = Instant.now();

    /** Reserva de la que proviene esta entrada. */
    private String reservaId;

    /**
     * Mini-outbox: false hasta que el evento "entrada comprada" se ha enviado a RabbitMQ.
     * Si el broker estaba caído, un job periódico reintenta las pendientes.
     */
    private boolean eventoPublicado = false;
}