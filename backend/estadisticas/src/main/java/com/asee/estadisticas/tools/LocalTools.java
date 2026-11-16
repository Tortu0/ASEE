package com.asee.estadisticas.tools;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClients;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import tools.jackson.databind.json.JsonMapper;

/** Herramientas de terminal para desarrollo. No expone endpoints ni claves privadas. */
public class LocalTools {
    public static void main(String[] args) throws Exception {
        switch(args[0]) {
            case "keys" -> keys(Path.of(args[1]));
            case "token" -> token(Path.of(args[1]),args[2],args[3],args[4],args[5],Path.of(args[6]));
            case "init-mongo" -> initMongo();
            default -> throw new IllegalArgumentException("Comando desconocido");
        }
    }
    private static void keys(Path dir) throws Exception {
        Files.createDirectories(dir);
        if (Files.exists(dir.resolve("public.pem")) && Files.exists(dir.resolve("private.pem"))) return;
        if (Files.exists(dir.resolve("public.pem")) || Files.exists(dir.resolve("private.pem"))) throw new IllegalStateException("Pareja de claves incompleta");
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
        var key=generator.generateKeyPair();
        Files.writeString(dir.resolve("private.pem"),pem("PRIVATE KEY",key.getPrivate().getEncoded()),StandardOpenOption.CREATE_NEW);
        Files.writeString(dir.resolve("public.pem"),pem("PUBLIC KEY",key.getPublic().getEncoded()),StandardOpenOption.CREATE_NEW);
        System.out.println("Claves locales preparadas en " + dir);
    }
    private static String pem(String type,byte[] data) {
        return "-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(data)+"\n-----END "+type+"-----\n";
    }
    private static void token(Path dir,String rol,String usuario,String issuer,String audience,Path output) throws Exception {
        UUID.fromString(usuario);
        if (!Set.of("staff","admin","visitante","socio").contains(rol)) throw new IllegalArgumentException("Rol desconocido");
        String pem=Files.readString(dir.resolve("private.pem")).replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        var json=JsonMapper.builder().build(); var encoder=Base64.getUrlEncoder().withoutPadding(); long now=Instant.now().getEpochSecond();
        String input=encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))+"."
                +encoder.encodeToString(json.writeValueAsBytes(Map.of("sub",usuario,"rol",rol,"iss",issuer,"aud",audience,"iat",now,"exp",now+3600)));
        var signature=Signature.getInstance("SHA256withRSA"); signature.initSign(key); signature.update(input.getBytes(StandardCharsets.US_ASCII));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,input+"."+encoder.encodeToString(signature.sign()));
        System.out.println("Token local guardado en " + output + ". Caduca en una hora.");
    }
    private static void initMongo() throws Exception {
        try (var client=MongoClients.create("mongodb://127.0.0.1:27017/?directConnection=true&serverSelectionTimeoutMS=5000")) {
            var admin=client.getDatabase("admin");
            try {
                var status=admin.runCommand(new Document("replSetGetStatus",1));
                if (!"asee-local".equals(status.getString("set"))) throw new IllegalStateException("La instancia usa otro replica set; configura MONGODB_URI sin modificarlo");
            } catch (MongoCommandException ex) {
                if (ex.getErrorCode()!=94) throw ex;
                admin.runCommand(new Document("replSetInitiate",new Document("_id","asee-local")
                        .append("members",List.of(new Document("_id",0).append("host","localhost:27017")))));
            }
            long deadline=System.currentTimeMillis()+30000;
            while (System.currentTimeMillis()<deadline) {
                if (Boolean.TRUE.equals(admin.runCommand(new Document("hello",1)).getBoolean("isWritablePrimary"))) {
                    System.out.println("Replica set asee-local preparado para transacciones"); return;
                }
                Thread.sleep(250);
            }
            throw new IllegalStateException("MongoDB no eligio un primario en 30 segundos");
        }
    }
}
