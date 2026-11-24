package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.exceptions.AiServiceException;
import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PollinationsImageClientTest {

    private static HttpServer servidor;
    private static final AtomicReference<String> ULTIMA_URI = new AtomicReference<>();

    private static byte[] jpegFalso() {
        byte[] bytes = new byte[3000];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[2998] = (byte) 0xFF;
        bytes[2999] = (byte) 0xD9;
        return bytes;
    }

    @BeforeAll
    static void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            ULTIMA_URI.set(troca.getRequestURI().toString());
            byte[] jpeg = jpegFalso();
            troca.getResponseHeaders().add("Content-Type", "image/jpeg");
            troca.sendResponseHeaders(200, jpeg.length);
            troca.getResponseBody().write(jpeg);
            troca.close();
        });
        servidor.start();
    }

    @AfterAll
    static void pararServidor() {
        servidor.stop(0);
    }

    private PollinationsImageClient client(boolean habilitado) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + servidor.getAddress().getPort())
                .build();
        return new PollinationsImageClient(restClient, habilitado, "flux");
    }

    @Test
    void geraJpegValidoEmBase64() {
        ImagemGerada imagem = client(true).gerarImagem("vestido rosa floral");

        assertEquals("image/jpeg", imagem.mimeType());
        byte[] bytes = Base64.getDecoder().decode(imagem.base64());
        assertEquals((byte) 0xFF, bytes[0]);
        assertEquals((byte) 0xD8, bytes[1]);

        String uri = ULTIMA_URI.get();
        assertTrue(uri.startsWith("/prompt/"), uri);
        assertTrue(uri.contains("model=flux"), uri);
        assertTrue(uri.contains("seed="), uri);
        assertTrue(uri.contains("nologo=true"), uri);
    }

    @Test
    void variacaoMudaASementeDaFoto() {
        PollinationsImageClient client = client(true);

        client.gerarImagem("mesmo look", 0);
        String primeira = ULTIMA_URI.get();
        client.gerarImagem("mesmo look", 1);
        String segunda = ULTIMA_URI.get();

        assertNotEquals(primeira, segunda);
    }

    @Test
    void provedorDesabilitadoFalhaDeFormaControlada() {
        AiServiceException ex = assertThrows(AiServiceException.class,
                () -> client(false).gerarImagem("vestido azul"));
        assertTrue(ex.getMessage().contains("desabilitado"));
    }
}
