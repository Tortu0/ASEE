package com.asee.compras.controller;

import com.asee.compras.dto.PedidoRequest;
import com.asee.compras.model.Pedido;
import com.asee.compras.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<Pedido> listar() {
        return pedidoService.listar();
    }

    @PostMapping
    public ResponseEntity<Pedido> crear(@RequestBody PedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crear(request));
    }
}