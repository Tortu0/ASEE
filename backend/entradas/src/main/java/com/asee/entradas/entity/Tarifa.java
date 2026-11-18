package com.asee.entradas.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Tarifa de un asiento para un partido, por zona y tipo de entrada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tarifas")
public class Tarifa {

    @Id
    private String tarifaId;

    private String partidoId;

    private String zona;

    private TipoEntrada tipoEntrada;

    private double precio;
}
