package com.asee.compras.web;

import com.asee.compras.aplicacion.CarritoServicio;
import com.asee.compras.aplicacion.CarritoServicio.DatosItem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/carrito")
public class CarritoController {

    /** Cuerpo de PUT /carrito/items/{itemId}. */
    public record DatosCantidad(Integer cantidad) {}

    private final CarritoServicio servicio;
    private final IdentidadResolver identidad;

    public CarritoController(CarritoServicio servicio, IdentidadResolver identidad) {
        this.servicio = servicio;
        this.identidad = identidad;
    }

    @GetMapping
    public Map<String, Object> ver(HttpServletRequest request) {
        return servicio.ver(identidad.quien(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> vaciar(HttpServletRequest request) {
        servicio.vaciar(identidad.quien(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> anadir(HttpServletRequest request, @RequestBody(required = false) DatosItem datos) {
        return ResponseEntity.status(201).body(servicio.anadir(identidad.quien(request), datos));
    }

    @PutMapping("/items/{itemId}")
    public Map<String, Object> cambiarCantidad(HttpServletRequest request, @PathVariable String itemId,
                                               @RequestBody(required = false) DatosCantidad datos) {
        return servicio.cambiarCantidad(identidad.quien(request), itemId, datos == null ? null : datos.cantidad());
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> quitar(HttpServletRequest request, @PathVariable String itemId) {
        servicio.quitar(identidad.quien(request), itemId);
        return ResponseEntity.noContent().build();
    }
}
