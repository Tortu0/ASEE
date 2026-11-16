package com.asee.institucional.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Document(collection = "historia")
public record Historia(
        @Id String id,
        String texto,
        LocalDate fechaFundacion,
        List<Hito> hitos
) {
    public static final String ID_UNICO = "club";
}