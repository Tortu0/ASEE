package com.asee.estadisticas.model;

public enum TipoEventoEstadistico {
    inicio_partido,
    inicio_periodo,
    fin_periodo,
    fin_partido,
    entra_pista,
    sale_pista,
    tiro_2_anotado,
    tiro_2_fallado,
    tiro_3_anotado,
    tiro_3_fallado,
    tiro_libre_anotado,
    tiro_libre_fallado,
    rebote_ofensivo,
    rebote_defensivo,
    asistencia,
    robo,
    perdida,
    tapon,
    tapon_recibido,
    falta_personal,
    falta_recibida;

    public boolean esGeneral() {
        return this == inicio_partido || this == inicio_periodo
                || this == fin_periodo || this == fin_partido;
    }

}
