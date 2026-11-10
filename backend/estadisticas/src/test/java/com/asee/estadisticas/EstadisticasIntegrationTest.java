package com.asee.estadisticas;

import com.asee.estadisticas.repository.*;
import com.mongodb.client.MongoClients;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="ASEE_INTEGRATION",matches="true")
class EstadisticasIntegrationTest {
    static final String P="20000000-0000-0000-0000-000000000001", P2="20000000-0000-0000-0000-000000000002", P3="20000000-0000-0000-0000-000000000003";
    static final String J="10000000-0000-0000-0000-000000000001", J2="10000000-0000-0000-0000-000000000002";
    static final String U="00000000-0000-0000-0000-000000000001";
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String DB="asee_test_"+UUID.randomUUID().toString().replace("-","");
    static final AtomicInteger DEPORTIVO_STATUS=new AtomicInteger(200);
    static HttpServer deportivo;
    static KeyPair key;
    static Path publicKey;
    @Autowired WebServerApplicationContext web;
    @Autowired PartidoRepository partidos;
    @Autowired EventoRepository eventos;
    @Autowired AjusteRepository ajustes;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource static void config(DynamicPropertyRegistry properties) throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); key=generator.generateKeyPair();
        publicKey=Files.createTempFile("asee-test-public-",".pem");
        Files.writeString(publicKey,"-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(key.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        deportivo=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        deportivo.createContext("/v1/",exchange -> {
            String path=exchange.getRequestURI().getPath(), id=path.substring(path.lastIndexOf('/')+1);
            int status=DEPORTIVO_STATUS.get(); Object result=Map.of("mensaje","No encontrado");
            if (status==200) {
                if (path.startsWith("/v1/partidos/") && Set.of(P,P2,P3).contains(id)) {
                    result=Map.of("partidoId",id,"temporada",id.equals(P3)?"2027-2028":"2026-2027","competicion",id.equals(P2)?"Copa":"Liga","jornada",1,"fechaHora","2026-11-04T18:30:00Z");
                } else if (path.startsWith("/v1/jugadores/") && Set.of(J,J2).contains(id)) result=Map.of("jugadorId",id);
                else status=404;
            }
            byte[] body=JSON.writeValueAsBytes(result); exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        deportivo.start();
        properties.add("spring.mongodb.uri",() -> System.getenv().getOrDefault("ASEE_TEST_MONGO_URI","mongodb://localhost:27017/?replicaSet=asee-local&serverSelectionTimeoutMS=5000"));
        properties.add("spring.mongodb.database",() -> DB);
        properties.add("app.deportivo.base-url",() -> "http://127.0.0.1:"+deportivo.getAddress().getPort()+"/v1");
        properties.add("app.security.public-key",() -> publicKey.toUri().toString());
        properties.add("app.security.issuer",() -> "asee-test");
        properties.add("app.security.audience",() -> "estadisticas");
        properties.add("app.websocket.max-subscriptions",() -> "1");
        properties.add("app.websocket.heartbeat-ms",() -> "200");
        properties.add("app.websocket.poll-ms",() -> "100");
    }
    @BeforeEach void clean() { DEPORTIVO_STATUS.set(200); eventos.deleteAll(); ajustes.deleteAll(); partidos.deleteAll(); }
    @AfterAll static void stop() throws Exception {
        if(deportivo!=null) deportivo.stop(0);
        if(publicKey!=null) Files.deleteIfExists(publicKey);
        // Únicamente la base aleatoria creada por esta ejecución, nunca la base del usuario.
        try (var client=MongoClients.create(System.getenv().getOrDefault("ASEE_TEST_MONGO_URI",
                "mongodb://localhost:27017/?replicaSet=asee-local&serverSelectionTimeoutMS=5000"))) {
            client.getDatabase(DB).drop();
        }
    }
    @AfterEach void noFailureContamination() { DEPORTIVO_STATUS.set(200); }
    @Test void healthYConsultasPublicas() throws Exception {
        assertThat(call("GET","/actuator/health/readiness",null,null).statusCode()).isEqualTo(200);
        var r=call("GET","/partidos/"+P+"/estadisticas",null,null); assertThat(r.statusCode()).isEqualTo(200);
        assertThat(tree(r).path("jugadores").size()).isZero();
        assertThat(call("GET","/partidos/"+UUID.randomUUID()+"/estadisticas",null,null).statusCode()).isEqualTo(404);
    }
    @Test void seguridadExigeFirmaCaducidadEmisorAudienciaYRol() throws Exception {
        String path="/partidos/"+P+"/eventos", body=event("seguridad",J,"tiro_2_anotado");
        assertThat(call("POST",path,body,null).statusCode()).isEqualTo(401);
        assertThat(call("POST",path,body,token("visitante")).statusCode()).isEqualTo(403);
        assertThat(call("POST",path,body,"invalido").statusCode()).isEqualTo(401);
        assertThat(call("POST",path,body,token("staff","otro","estadisticas",Instant.now().plusSeconds(1000),key)).statusCode()).isEqualTo(401);
        assertThat(call("POST",path,body,token("staff","asee-test","otra",Instant.now().plusSeconds(1000),key)).statusCode()).isEqualTo(401);
        assertThat(call("POST",path,body,token("staff","asee-test","estadisticas",Instant.now().minusSeconds(1000),key)).statusCode()).isEqualTo(401);
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        assertThat(call("POST",path,body,token("staff","asee-test","estadisticas",Instant.now().plusSeconds(1000),generator.generateKeyPair())).statusCode()).isEqualTo(401);
        assertThat(eventos.count()).isZero();
        assertThat(call("POST",path,body,token("admin")).statusCode()).isEqualTo(201);
    }
    @Test void duplicadosSeLimitanAlPartido() throws Exception {
        assertThat(post(P,"misma",J,"tiro_2_anotado").statusCode()).isEqualTo(201);
        assertThat(post(P,"misma",J,"tiro_2_anotado").statusCode()).isEqualTo(409);
        assertThat(post(P2,"misma",J,"tiro_3_anotado").statusCode()).isEqualTo(201);
        assertThat(player(P,J).path("puntos").asInt()).isEqualTo(2);
        assertThat(player(P2,J).path("puntos").asInt()).isEqualTo(3);
    }
    @Test void desbordamientoDeCamposCalculadosSeRechazaAntesDeGuardar() throws Exception {
        assertThat(putPlayer(P,J,"{\"rebotesOfensivos\":2147483647,\"rebotesDefensivos\":1}").statusCode()).isEqualTo(400);
        assertThat(putPlayer(P,J,"{\"tiros2Intentados\":2147483647,\"tiros3Intentados\":1}").statusCode()).isEqualTo(400);
        assertThat(partidos.count()).isZero();
        assertThat(ajustes.count()).isZero();
        assertThat(putPlayer(P,J,"{\"rebotesOfensivos\":2147483647}").statusCode()).isEqualTo(200);
        assertThat(post(P,"rebote-excesivo",J,"rebote_defensivo").statusCode()).isEqualTo(409);
        assertThat(eventos.count()).isZero();
        assertThat(player(P,J).path("rebotesTotales").asInt()).isEqualTo(Integer.MAX_VALUE);
    }
    @Test void concurrenciaNoDuplicaEventosNiPierdeContadores() throws Exception {
        String token=token("staff");
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> call("POST","/partidos/"+P+"/eventos",event("duplicado",J,"tiro_2_anotado"),token).statusCode());
            var b=executor.submit(() -> call("POST","/partidos/"+P+"/eventos",event("duplicado",J,"tiro_2_anotado"),token).statusCode());
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(201,409);
            a=executor.submit(() -> call("POST","/partidos/"+P+"/eventos",event("nuevo1",J,"tiro_2_anotado"),token).statusCode());
            b=executor.submit(() -> call("POST","/partidos/"+P+"/eventos",event("nuevo2",J,"tiro_3_anotado"),token).statusCode());
            assertThat(List.of(a.get(),b.get())).containsOnly(201);
        }
        assertThat(player(P,J).path("puntos").asInt()).isEqualTo(7);
    }
    @Test void correccionesPersistenYAnulacionIncoherenteNoEscribe() throws Exception {
        var e1=tree(post(P,"dos1",J,"tiro_2_anotado")); post(P,"dos2",J,"tiro_2_anotado");
        String correction="{\"puntos\":2,\"tiros2Anotados\":1,\"tiros2Intentados\":1,\"segundosJugados\":900,\"masMenos\":-3,\"titular\":true}";
        assertThat(putPlayer(P,J,correction).statusCode()).isEqualTo(200);
        assertThat(putPlayer(P,J,correction).statusCode()).isEqualTo(200);
        var e3=tree(post(P,"tres",J,"tiro_3_anotado")); assertThat(player(P,J).path("puntos").asInt()).isEqualTo(5);
        long version=partidos.findById(UUID.fromString(P)).orElseThrow().versionPublica();
        assertThat(call("DELETE","/partidos/"+P+"/eventos/"+e1.path("eventoId").asString(),null,token("staff")).statusCode()).isEqualTo(204);
        // Anular la otra canasta de dos dejaría tiros2Anotados negativos por el ajuste -1.
        String other=eventos.findByPartidoIdOrderByRegistradoEnAsc(UUID.fromString(P)).stream().filter(e -> e.clientEventoId().equals("dos2")).findFirst().orElseThrow().eventoId().toString();
        assertThat(call("DELETE","/partidos/"+P+"/eventos/"+other,null,token("staff")).statusCode()).isEqualTo(409);
        assertThat(eventos.findById(UUID.fromString(other)).orElseThrow().anulado()).isFalse();
        assertThat(partidos.findById(UUID.fromString(P)).orElseThrow().versionPublica()).isEqualTo(version+1);
        assertThat(player(P,J).path("segundosJugados").asInt()).isEqualTo(900);
        assertThat(player(P,J).path("masMenos").asInt()).isEqualTo(-3);
        assertThat(call("DELETE","/partidos/"+P+"/eventos/"+e3.path("eventoId").asString(),null,token("admin")).statusCode()).isEqualTo(204);
        long stable=partidos.findById(UUID.fromString(P)).orElseThrow().versionPublica();
        assertThat(call("DELETE","/partidos/"+P+"/eventos/"+e3.path("eventoId").asString(),null,token("admin")).statusCode()).isEqualTo(204);
        assertThat(partidos.findById(UUID.fromString(P)).orElseThrow().versionPublica()).isEqualTo(stable);
        assertThat(post(P,"tres",J,"tiro_3_anotado").statusCode()).isEqualTo(409);
        assertThat(ajustes.count()).isEqualTo(2);
    }
    @Test void putDePartidoEsAtomicoYConservaJugadoresOmitidos() throws Exception {
        String first="{\"jugadorId\":\""+J+"\",\"estadisticas\":{\"puntos\":2,\"tiros2Anotados\":1,\"tiros2Intentados\":1}}";
        String invalid="{\"jugadorId\":\""+J2+"\",\"estadisticas\":{\"puntos\":5}}";
        assertThat(call("PUT","/partidos/"+P+"/estadisticas","{\"jugadores\":["+first+","+invalid+"]}",token("staff")).statusCode()).isEqualTo(400);
        assertThat(partidos.count()).isZero(); assertThat(ajustes.count()).isZero();
        String valid=invalid.replace("\"puntos\":5","\"asistencias\":1");
        assertThat(call("PUT","/partidos/"+P+"/estadisticas","{\"jugadores\":["+first+","+valid+"]}",token("staff")).statusCode()).isEqualTo(200);
        assertThat(putPlayer(P,J,"{\"titular\":false}").statusCode()).isEqualTo(200);
        assertThat(player(P,J2).path("asistencias").asInt()).isEqualTo(1);
        assertThat(putPlayer(P,J,"{\"puntos\":0,\"tiros2Anotados\":0}").statusCode()).isEqualTo(200);
        assertThat(player(P,J).path("puntos").asInt()).isZero();
    }
    @Test void validacionesHttpDevuelvenErroresDelContrato() throws Exception {
        for(String body:List.of("{}","{\"puntos\":null}","{\"puntos\":1.2}","{\"valoracion\":4}","{\"faltasCometidas\":6}")) {
            var r=putPlayer(P,J,body); assertThat(r.statusCode()).isEqualTo(400); assertThat(tree(r).path("codigo").asInt()).isEqualTo(400);
        }
        assertThat(call("GET","/temporadas/mal/estadisticas",null,null).statusCode()).isEqualTo(400);
        assertThat(call("GET","/temporadas/2026-2027/lideres?metrica=puntos&limite=51",null,null).statusCode()).isEqualTo(400);
        assertThat(call("GET","/partidos/"+P+"/eventos?tipo=desconocido",null,null).statusCode()).isEqualTo(400);
        assertThat(call("GET","/partidos/no-uuid/estadisticas",null,null).statusCode()).isEqualTo(400);
        assertThat(post(P,"sinjugador",null,"asistencia").statusCode()).isEqualTo(400);
        assertThat(post(P,"inicio",null,"inicio_partido").statusCode()).isEqualTo(201);
    }
    @Test void filtrosTotalesPromediosYRankings() throws Exception {
        post(P,"a",J,"tiro_2_anotado"); post(P2,"b",J,"tiro_3_anotado"); post(P3,"c",J,"tiro_2_anotado"); post(P,"d",J2,"tiro_2_anotado");
        putPlayer(P,J,"{\"segundosJugados\":600}"); putPlayer(P2,J,"{\"segundosJugados\":1200}");
        var s=tree(call("GET","/jugadores/"+J+"/estadisticas?temporada=2026-2027&incluirPartidos=true",null,null));
        assertThat(s.path("totales").path("puntos").asInt()).isEqualTo(5);
        assertThat(s.path("promedios").path("puntosPorPartido").asDouble()).isEqualTo(2.5);
        assertThat(s.path("promedios").path("minutosPorPartido").asDouble()).isEqualTo(15);
        assertThat(s.path("detallePartidos").size()).isEqualTo(2);
        assertThat(tree(call("GET","/jugadores/"+J+"/estadisticas?temporada=2026-2027",null,null)).has("detallePartidos")).isFalse();
        assertThat(tree(call("GET","/jugadores/"+J+"/partidos?temporada=2026-2027&competicion=Copa",null,null)).size()).isEqualTo(1);
        var rank=tree(call("GET","/temporadas/2026-2027/lideres?metrica=puntos&minimoPartidos=2&limite=1",null,null));
        assertThat(rank.path("jugadores").size()).isEqualTo(1); assertThat(rank.path("jugadores").get(0).path("jugadorId").asString()).isEqualTo(J);
        assertThat(tree(call("GET","/temporadas/2026-2027/estadisticas",null,null)).size()).isEqualTo(2);
        assertThat(tree(call("GET","/temporadas/2099-2100/estadisticas",null,null)).size()).isZero();
        assertThat(tree(call("GET","/temporadas/2099-2100/lideres?metrica=puntos",null,null)).path("jugadores").size()).isZero();
        var events=tree(call("GET","/partidos/"+P+"/eventos?jugadorId="+J+"&tipo=tiro_2_anotado",null,null));
        assertThat(events.size()).isEqualTo(1);
        assertThat(tree(call("GET","/partidos/"+P+"/eventos?desde="+events.get(0).path("registradoEn").asString(),null,null)).size()).isEqualTo(1);
    }
    @Test void fallosDeDeportivoNoConfirmanEscrituras() throws Exception {
        DEPORTIVO_STATUS.set(503); assertThat(post(P,"caida",J,"tiro_2_anotado").statusCode()).isEqualTo(503);
        assertThat(eventos.count()).isZero(); assertThat(partidos.count()).isZero();
        DEPORTIVO_STATUS.set(200);
        String body=event("relacionado",J,"asistencia").replace("}",",\"jugadorRelacionadoId\":\""+UUID.randomUUID()+"\"}");
        assertThat(call("POST","/partidos/"+P+"/eventos",body,token("staff")).statusCode()).isEqualTo(404);
        assertThat(eventos.count()).isZero();
    }
    @Test void sextaFaltaNoDejaEventoNiActualizaEstado() throws Exception {
        for(int i=0;i<5;i++) assertThat(post(P,"falta"+i,J,"falta_personal").statusCode()).isEqualTo(201);
        assertThat(post(P,"falta5",J,"falta_personal").statusCode()).isEqualTo(409);
        assertThat(eventos.count()).isEqualTo(5); assertThat(player(P,J).path("faltasCometidas").asInt()).isEqualTo(5);
    }
    @Test void websocketEsPublicoDeLecturaYEnviaVersiones() throws Exception {
        var listener=new WsListener();
        var ws=http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+web.getWebServer().getPort()+"/v1/ws"),listener).get(5,TimeUnit.SECONDS);
        try {
            ws.sendText("{\"tipo\":\"suscribir\",\"partidoId\":\""+P+"\"}",true).join();
            assertThat(listener.next("estado").path("version").asLong()).isZero();
            post(P,"ws",J,"tiro_2_anotado"); var state=listener.next("estado");
            assertThat(state.path("version").asLong()).isEqualTo(1);
            assertThat(state.path("datos").path("jugadores").get(0).path("puntos").asInt()).isEqualTo(2);
            assertThat(state.path("datos")).isEqualTo(tree(call("GET","/partidos/"+P+"/estadisticas",null,null)));
            ws.sendText("{\"tipo\":\"suscribir\",\"partidoId\":\""+P2+"\"}",true).join();
            assertThat(listener.next("error").path("codigo").asString()).isEqualTo("limite_suscripciones");
            ws.sendText("{\"tipo\":\"registrar\",\"partidoId\":\""+P+"\"}",true).join();
            assertThat(listener.next("error").path("codigo").asString()).isEqualTo("mensaje_invalido"); assertThat(eventos.count()).isEqualTo(1);
            ws.sendText("{\"tipo\":\"cancelar_suscripcion\",\"partidoId\":\""+P+"\"}",true).join();
            ws.sendText("{\"tipo\":\"suscribir\",\"partidoId\":\""+UUID.randomUUID()+"\"}",true).join();
            assertThat(listener.next("error").path("codigo").asString()).isEqualTo("partido_no_encontrado");
            assertThat(listener.heartbeats.get()).isPositive();
        } finally { ws.sendClose(WebSocket.NORMAL_CLOSURE,"fin").join(); }
    }
    private HttpResponse<String> post(String p,String client,String j,String type) throws Exception { return call("POST","/partidos/"+p+"/eventos",event(client,j,type),token("staff")); }
    private HttpResponse<String> putPlayer(String p,String j,String body) throws Exception { return call("PUT","/partidos/"+p+"/jugadores/"+j+"/estadisticas",body,token("staff")); }
    private JsonNode player(String p,String j) throws Exception { var r=call("GET","/partidos/"+p+"/jugadores/"+j+"/estadisticas",null,null); assertThat(r.statusCode()).isEqualTo(200); return tree(r); }
    private static String event(String client,String j,String type) { var data=new HashMap<String,Object>(); data.put("clientEventoId",client);data.put("tipo",type);data.put("periodo",1);if(j!=null)data.put("jugadorId",j);return JSON.writeValueAsString(data); }
    private HttpResponse<String> call(String method,String path,String body,String token) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+web.getWebServer().getPort()+"/v1"+path)).timeout(Duration.ofSeconds(20));
        if(token!=null)req.header("Authorization","Bearer "+token);
        if(body!=null)req.header("Content-Type","application/json");
        return http.send(req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static JsonNode tree(HttpResponse<String> r) { return JSON.readTree(r.body()); }
    private static String token(String role) throws Exception { return token(role,"asee-test","estadisticas",Instant.now().plusSeconds(3600),key); }
    private static String token(String role,String issuer,String audience,Instant expiry,KeyPair signingKey) throws Exception {
        var b64=Base64.getUrlEncoder().withoutPadding();
        String input=b64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))+"."+b64.encodeToString(JSON.writeValueAsBytes(Map.of("sub",U,"rol",role,"iss",issuer,"aud",audience,"exp",expiry.getEpochSecond())));
        var sig=Signature.getInstance("SHA256withRSA");sig.initSign(signingKey.getPrivate());sig.update(input.getBytes(StandardCharsets.US_ASCII));return input+"."+b64.encodeToString(sig.sign());
    }
    static class WsListener implements WebSocket.Listener {
        final BlockingQueue<JsonNode> messages=new LinkedBlockingQueue<>(); final AtomicInteger heartbeats=new AtomicInteger(); final StringBuilder pending=new StringBuilder();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last) {
            pending.append(text);
            if(last) {
                var json=JSON.readTree(pending.toString());pending.setLength(0);
                if(json.path("tipo").asString().equals("heartbeat")) { heartbeats.incrementAndGet();ws.sendText("{\"tipo\":\"pong\"}",true); }
                else messages.add(json);
            }
            ws.request(1);return null;
        }
        JsonNode next(String type) throws Exception {
            var value=messages.poll(5,TimeUnit.SECONDS);assertThat(value).as("mensaje WebSocket %s",type).isNotNull();assertThat(value.path("tipo").asString()).isEqualTo(type);return value;
        }
    }
}
