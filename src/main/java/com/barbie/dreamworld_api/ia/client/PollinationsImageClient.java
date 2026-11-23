package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerador de imagem alternativo e gratuito, usado como fallback quando o
 * Gemini Nano Banana não tem quota disponível (Free Tier = 0 imagens/dia).
 *
 * O prompt é o mesmo já montado a partir do look gerado pelo Gemini, então a
 * imagem continua a representar exatamente o look sugerido pela IA de texto.
 */
@Slf4j
@Component
public class PollinationsImageClient {

    private static final String BASE_URL = "https://image.pollinations.ai/prompt/";
    private static final int TIMEOUT_SEGUNDOS = 90;

    public ImagemGerada gerarImagem(String prompt) {
        try {
            String encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            int seed = ThreadLocalRandom.current().nextInt(1_000_000);
            String url = BASE_URL + encoded
                    + "?width=512&height=683&nologo=true&seed=" + seed;

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SEGUNDOS))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200 || response.body().length == 0) {
                throw new IllegalStateException(
                        "Gerador alternativo respondeu " + response.statusCode());
            }

            String mime = response.headers().firstValue("Content-Type")
                    .orElse("image/jpeg")
                    .split(";")[0]
                    .trim();
            if (!mime.startsWith("image/")) {
                mime = "image/jpeg";
            }

            String base64 = Base64.getEncoder().encodeToString(response.body());
            log.info("Imagem alternativa gerada: {} bytes (mime={})", response.body().length, mime);
            return new ImagemGerada(mime, base64);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Geração de imagem interrompida.", ie);
        } catch (Exception ex) {
            log.warn("Falha no gerador de imagem alternativo: {}", ex.getMessage());
            throw new IllegalStateException("Não foi possível gerar a imagem.", ex);
        }
    }
}
