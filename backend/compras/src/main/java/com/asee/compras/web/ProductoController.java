package com.asee.compras.web;

import com.asee.compras.aplicacion.ProductoServicio;
import com.asee.compras.aplicacion.ProductoServicio.DatosProducto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoServicio servicio;
    private final IdentidadResolver identidad;

    public ProductoController(ProductoServicio servicio, IdentidadResolver identidad) {
        this.servicio = servicio;
        this.identidad = identidad;
    }

    // Catálogo público (sin autenticación)
    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String categoria,
                                            @RequestParam(required = false) String jugadorId,
                                            @RequestParam(required = false) String patrocinadorId,
                                            @RequestParam(required = false) Integer pagina,
                                            @RequestParam(required = false) Integer tamano) {
        return servicio.listar(categoria, jugadorId, patrocinadorId, pagina, tamano);
    }

    @GetMapping("/{productoId}")
    public Map<String, Object> obtener(@PathVariable String productoId) {
        return servicio.obtener(productoId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(HttpServletRequest request,
                                                     @RequestBody(required = false) DatosProducto datos) {
        Map<String, Object> creado = servicio.crear(identidad.quien(request), datos);
        return ResponseEntity.status(201).header("Location", "/v1/productos/" + creado.get("productoId")).body(creado);
    }

    @PutMapping("/{productoId}")
    public Map<String, Object> actualizar(HttpServletRequest request, @PathVariable String productoId,
                                          @RequestBody(required = false) DatosProducto datos) {
        return servicio.actualizar(identidad.quien(request), productoId, datos);
    }

    @DeleteMapping("/{productoId}")
    public ResponseEntity<Void> eliminar(HttpServletRequest request, @PathVariable String productoId) {
        servicio.eliminar(identidad.quien(request), productoId);
        return ResponseEntity.noContent().build();
    }
}
