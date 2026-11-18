package com.asee.compras.infraestructura;

import com.asee.compras.aplicacion.PasarelaPago;
import com.asee.compras.aplicacion.ResultadoPago;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pasarela de pago SIMULADA (todavía no hay una real). Es idempotente por referencia, como exigiría la real.
 * Modo (propiedad compras.pago.simulado):
 *   ok        -> todos los cobros se aprueban (por defecto)
 *   rechazado -> todos se rechazan (para probar el 402)
 *   incierto  -> el cobro se hace pero la respuesta se "pierde" (503); al consultarlo después consta como cobrado
 * El registro de cobros vive en memoria: se pierde al reiniciar el servicio.
 */
public class PasarelaSimulada implements PasarelaPago {

    private final String modo;
    private final Map<String, ResultadoPago> cobros = new ConcurrentHashMap<>();

    public PasarelaSimulada(String modo) {
        this.modo = modo == null ? "ok" : modo.trim().toLowerCase();
    }

    @Override
    public ResultadoPago cobrar(String referencia, BigDecimal importe, String metodoPago) {
        ResultadoPago previo = cobros.get(referencia);
        if (previo != null) return previo;   // misma referencia: no se cobra dos veces
        switch (modo) {
            case "rechazado":
                cobros.put(referencia, ResultadoPago.RECHAZADO);
                return ResultadoPago.RECHAZADO;
            case "incierto":
                cobros.put(referencia, ResultadoPago.COBRADO);
                return ResultadoPago.INCIERTO;
            default:
                cobros.put(referencia, ResultadoPago.COBRADO);
                return ResultadoPago.COBRADO;
        }
    }

    @Override
    public ResultadoPago consultar(String referencia) {
        return cobros.getOrDefault(referencia, ResultadoPago.DESCONOCIDO);
    }

    @Override
    public boolean reembolsar(String referencia, BigDecimal importe) {
        return cobros.get(referencia) == ResultadoPago.COBRADO;
    }
}
