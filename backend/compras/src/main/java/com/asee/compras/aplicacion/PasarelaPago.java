package com.asee.compras.aplicacion;

import java.math.BigDecimal;

/** Pasarela de pago externa (compartida con Entradas). La referencia es estable ante reintentos. */
public interface PasarelaPago {
    ResultadoPago cobrar(String referencia, BigDecimal importe, String metodoPago);

    /** Estado de un cobro ya intentado: se consulta antes de repetirlo. */
    ResultadoPago consultar(String referencia);

    /** true si la devolución quedó confirmada; false (o excepción) si debe conciliarse después. */
    boolean reembolsar(String referencia, BigDecimal importe);
}
