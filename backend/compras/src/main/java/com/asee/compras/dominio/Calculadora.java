package com.asee.compras.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class Calculadora {

    private Calculadora() {}

    public record Totales(BigDecimal subtotal, BigDecimal descuentoSocio, BigDecimal total) {}

    public static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** descuentoPorcentaje entre 0 y 100 (null = 0). El descuento es un importe redondeado a céntimos. */
    public static Totales totales(List<ItemCarrito> items, BigDecimal descuentoPorcentaje) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemCarrito i : items) {
            subtotal = subtotal.add(i.precioUnitario.multiply(BigDecimal.valueOf(i.cantidad)));
        }
        subtotal = dinero(subtotal);
        BigDecimal pct = descuentoPorcentaje == null ? BigDecimal.ZERO : descuentoPorcentaje;
        if (pct.signum() < 0) pct = BigDecimal.ZERO;
        if (pct.compareTo(BigDecimal.valueOf(100)) > 0) pct = BigDecimal.valueOf(100);
        BigDecimal descuento = dinero(subtotal.multiply(pct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return new Totales(subtotal, descuento, dinero(subtotal.subtract(descuento)));
    }
}
