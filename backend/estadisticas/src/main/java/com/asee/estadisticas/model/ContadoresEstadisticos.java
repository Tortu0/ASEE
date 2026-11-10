package com.asee.estadisticas.model;

/** Valores aditivos: los ajustes pueden ser negativos, aunque el resultado final no. */
public record ContadoresEstadisticos(
        int puntos,
        int tiros2Anotados,
        int tiros2Intentados,
        int tiros3Anotados,
        int tiros3Intentados,
        int tirosLibresAnotados,
        int tirosLibresIntentados,
        int rebotesOfensivos,
        int rebotesDefensivos,
        int asistencias,
        int robos,
        int perdidas,
        int tapones,
        int taponesRecibidos,
        int faltasCometidas,
        int faltasRecibidas
) {
    public static ContadoresEstadisticos ceros() {
        return new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
