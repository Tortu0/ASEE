package com.asee.compras.web;

import com.asee.compras.aplicacion.PedidoServicio;
import com.asee.compras.aplicacion.PedidoServicio.DatosPedido;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    /** Cuerpo de PUT /pedidos/{pedidoId}/estado. */
    public record DatosEstado(String estado) {}

    private final PedidoServicio servicio;
    private final IdentidadResolver identidad;

    public PedidoController(PedidoServicio servicio, IdentidadResolver identidad) {
        this.servicio = servicio;
        this.identidad = identidad;
    }

    /** La Idempotency-Key se valida en el servicio para devolver el 400 con el formato de error del contrato. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(HttpServletRequest request,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
                                                     @RequestBody(required = false) DatosPedido datos) {
        Map<String, Object> pedido = servicio.crear(identidad.quien(request), claveIdempotencia, datos);
        return ResponseEntity.status(201).header("Location", "/v1/pedidos/" + pedido.get("pedidoId")).body(pedido);
    }

    @GetMapping
    public List<Map<String, Object>> listar(HttpServletRequest request,
                                            @RequestParam(required = false) String usuarioId,
                                            @RequestParam(required = false) String estado) {
        return servicio.listar(identidad.quien(request), usuarioId, estado);
    }

    @GetMapping("/{pedidoId}")
    public Map<String, Object> obtener(HttpServletRequest request, @PathVariable String pedidoId) {
        return servicio.obtener(identidad.quien(request), pedidoId);
    }

    @DeleteMapping("/{pedidoId}")
    public ResponseEntity<Void> cancelar(HttpServletRequest request, @PathVariable String pedidoId) {
        servicio.cancelar(identidad.quien(request), pedidoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{pedidoId}/estado")
    public Map<String, Object> cambiarEstado(HttpServletRequest request, @PathVariable String pedidoId,
                                             @RequestBody(required = false) DatosEstado datos) {
        return servicio.cambiarEstado(identidad.quien(request), pedidoId, datos == null ? null : datos.estado());
    }
}
