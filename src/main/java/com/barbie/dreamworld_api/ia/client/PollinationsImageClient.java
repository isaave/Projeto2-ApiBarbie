package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.exceptions.AiServiceException;
import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Gera uma foto realista do look usando o provedor público Pollinations
 * (modelo FLUX), sem exigir chave de API.
 *
 * É usado quando o gerador de imagens do Gemini não tem cota — situação
 * normal no tier gratuito. Ainda assim, continua sendo um último recurso:
 * se falhar, o serviço volta para a ilustração vetorial determinística.
 */
@Slf4j
@Component
public class PollinationsImageClient {

    private static final int LARGURA = 768;
    private static final int ALTURA = 1024;

    private final RestClient restClient;
    private final boolean habilitado;
    private final String modelo;

    public PollinationsImageClient(
            @Qualifier("pollinationsRestClient") RestClient pollinationsRestClient,
            @Value("${image.pollinations.enabled:true}") boolean habilitado,
            @Value("${image.pollinations.model:flux}") String modelo) {
        this.restClient = pollinationsRestClient;
        this.habilitado = habilitado;
        this.modelo = modelo;
    }

    public ImagemGerada gerarImagem(String prompt) {
        return gerarImagem(prompt, 0);
    }

    /** A variação muda a semente: cada clique em "Outra imagem" dá uma foto diferente. */
    public ImagemGerada gerarImagem(String prompt, int variacao) {
        if (!habilitado) {
            throw new AiServiceException(
                    "Gerador de fotos externo desabilitado.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (prompt == null || prompt.isBlank()) {
            throw new AiServiceException("Prompt de imagem vazio.", HttpStatus.BAD_REQUEST);
        }

        int semente = Math.floorMod(prompt.hashCode() + variacao * 7919, 1_000_000);

        try {
            ResponseEntity<byte[]> resposta = restClient.get()
                    .uri(uri -> uri
                            .path("/prompt/{prompt}")
                            .queryParam("width", LARGURA)
                            .queryParam("height", ALTURA)
                            .queryParam("seed", semente)
                            .queryParam("model", modelo)
                            .queryParam("nologo", "true")
                            .queryParam("enhance", "true")
                            .build(Map.of("prompt", prompt)))
                    .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] bytes = resposta.getBody();
            if (bytes == null || bytes.length < 2048 || !pareceImagem(bytes)) {
                throw new AiServiceException(
                        "O gerador de fotos devolveu um arquivo inválido.",
                        HttpStatus.BAD_GATEWAY);
            }

            MediaType tipo = resposta.getHeaders().getContentType();
            String mimeType = tipo != null && tipo.isConcrete() && tipo.getType().equals("image")
                    ? tipo.toString()
                    : MediaType.IMAGE_JPEG_VALUE;

            log.info("Foto realista gerada ({} bytes, mime={}).", bytes.length, mimeType);
            return new ImagemGerada(mimeType, Base64.getEncoder().encodeToString(bytes));

        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new AiServiceException(
                    "O gerador de fotos está recebendo muitas requisições. Aguarde um momento.",
                    HttpStatus.TOO_MANY_REQUESTS, ex);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.warn("Pollinations respondeu {}: {}", ex.getStatusCode(), ex.getMessage());
            throw new AiServiceException(
                    "O gerador de fotos externo falhou.", HttpStatus.BAD_GATEWAY, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Pollinations indisponível: {}", ex.getMessage());
            throw new AiServiceException(
                    "O gerador de fotos externo está indisponível.",
                    HttpStatus.SERVICE_UNAVAILABLE, ex);
        }
    }

    private boolean pareceImagem(byte[] bytes) {
        boolean jpeg = bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF;
        boolean png = bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean webp = bytes.length > 11
                && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
        return jpeg || png || webp;
    }
}
