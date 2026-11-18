package com.asee.entradas.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * Documento MongoDB que representa un asiento de un partido.
 * Un asiento pertenece a exactamente un partido (no se comparten entre partidos).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "asientos")
public class Asiento {

    @Id
    private String asientoId;

    @Version
    private Long version;

    /** ID del partido al que pertenece este asiento. */
    @Indexed
    private String partidoId;

    /** Zona (ej: A, B, VIP). */
    private String zona;

    /** Número de fila. */
    private int fila;

    /** Número de asiento en la fila. */
    private int numero;

    /** Estado actual del asiento. */
    private EstadoAsiento estado = EstadoAsiento.libre;
}
