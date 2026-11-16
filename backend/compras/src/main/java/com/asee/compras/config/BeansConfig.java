package com.asee.compras.config;

import com.asee.compras.aplicacion.Autenticador;
import com.asee.compras.aplicacion.CarritoRepositorio;
import com.asee.compras.aplicacion.CarritoServicio;
import com.asee.compras.aplicacion.PasarelaPago;
import com.asee.compras.aplicacion.PedidoRepositorio;
import com.asee.compras.aplicacion.PedidoServicio;
import com.asee.compras.aplicacion.ProductoRepositorio;
import com.asee.compras.aplicacion.ProductoServicio;
import com.asee.compras.aplicacion.PublicadorEventos;
import com.asee.compras.aplicacion.UsuariosPort;
import com.asee.compras.infraestructura.PasarelaSimulada;
import com.asee.compras.infraestructura.UsuariosHttpClient;
import com.asee.compras.persistencia.CarritoMongo;
import com.asee.compras.persistencia.PedidoMongo;
import com.asee.compras.persistencia.ProductoMongo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Duration;

/** Conecta la lógica de negocio (Java puro, en los paquetes dominio/aplicacion/infraestructura) con Spring. */
@Configuration
public class BeansConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    public Autenticador autenticador(@Value("${compras.security.jwt-secret:}") String jwtSecret, Clock reloj) {
        return new Autenticador(jwtSecret, reloj);
    }

    @Bean
    public UsuariosPort usuariosPort(@Value("${compras.usuarios.url}") String url) {
        return new UsuariosHttpClient(url);
    }

    @Bean
    public PasarelaPago pasarelaPago(@Value("${compras.pago.simulado:ok}") String modo) {
        return new PasarelaSimulada(modo);
    }

    @Bean
    public ProductoRepositorio productoRepositorio(MongoTemplate mongo) {
        return new ProductoMongo(mongo);
    }

    @Bean
    public CarritoRepositorio carritoRepositorio(MongoTemplate mongo) {
        return new CarritoMongo(mongo);
    }

    @Bean
    public PedidoRepositorio pedidoRepositorio(MongoTemplate mongo) {
        return new PedidoMongo(mongo);
    }

    @Bean
    public ProductoServicio productoServicio(ProductoRepositorio productos) {
        return new ProductoServicio(productos, ProductoServicio::nuevoId);
    }

    @Bean
    public CarritoServicio carritoServicio(ProductoRepositorio productos, CarritoRepositorio carritos, UsuariosPort usuarios) {
        return new CarritoServicio(productos, carritos, usuarios, ProductoServicio::nuevoId);
    }

    @Bean
    public PedidoServicio pedidoServicio(ProductoRepositorio productos, CarritoRepositorio carritos, PedidoRepositorio pedidos,
                                         UsuariosPort usuarios, PasarelaPago pasarela, PublicadorEventos publicador, Clock reloj,
                                         @Value("${compras.pago.en-curso-max-segundos:60}") long pagoEnCursoMaxSegundos) {
        return new PedidoServicio(productos, carritos, pedidos, usuarios, pasarela, publicador, reloj,
                Duration.ofSeconds(pagoEnCursoMaxSegundos));
    }
}
