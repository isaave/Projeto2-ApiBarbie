package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.exceptions.AiServiceException;
import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsula a geração de imagem via Gemini (modelos Nano Banana) usando a
 * Interactions API, que é a interface atual recomendada para image generation.
 *
 * O fluxo é: o texto do look já foi gerado por {@link GeminiClient}; aqui
 * montamos um prompt de imagem a partir dessas informações e pedimos ao modelo
 * que desenhe a Barbie com aquele visual.
 */
@Slf4j
@Component
public class GeminiImageClient {

    private static final int MAX_TENTATIVAS = 2;
    private static final long ESPERA_ENTRE_TENTATIVAS_MS = 500;

    // Formato retrato favorece uma boneca em corpo inteiro.
    private static final String ASPECT_RATIO = "3:4";
    private static final String IMAGE_SIZE = "1K";
    // A Interactions API atual só aceita image/jpeg em response_format.mime_type
    private static final String MIME_TYPE = "image/jpeg";
    // Revisão da API exigida pelos exemplos oficiais da Interactions API.
    private static final String API_REVISION = "2026-05-20";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiImageClient(
            @Qualifier("geminiImageRestClient") RestClient geminiImageRestClient,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.image-model}") String model) {
        this.restClient = geminiImageRestClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Gera a imagem do look e devolve os bytes em base64 + o MIME type.
     */
    public ImagemGerada gerarImagem(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException(
                    "Assistente de Estilo não configurado: defina a variável de ambiente GEMINI_API_KEY.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(Map.of(
                "type", "text",
                "text", prompt
        )));
        body.put("response_format", Map.of(
                "type", "image",
                "mime_type", MIME_TYPE,
                "aspect_ratio", ASPECT_RATIO,
                "image_size", IMAGE_SIZE
        ));
        // Não precisamos de estado/conversação: evita armazenar o prompt no provedor.
        body.put("store", false);

        RuntimeException ultimaFalhaTransitoria = null;

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                Map<String, Object> response = restClient.post()
                        .uri("/v1beta/interactions")
                        .header("x-goog-api-key", apiKey)
                        .header("Api-Revision", API_REVISION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                return extrairImagem(response);

            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw new AiServiceException(
                        "O provedor de imagens está recebendo muitas requisições agora. Aguarde alguns segundos e tente novamente.",
                        HttpStatus.TOO_MANY_REQUESTS, ex);

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
                log.error("Gemini rejeitou a chave de API ao gerar imagem: {}", ex.getResponseBodyAsString());
                throw new AiServiceException(
                        "Não foi possível autenticar com o provedor de imagens. Verifique a GEMINI_API_KEY configurada.",
                        HttpStatus.SERVICE_UNAVAILABLE, ex);

            } catch (HttpClientErrorException ex) {
                log.error("Gemini respondeu {} ao gerar imagem: {}",
                        ex.getStatusCode(), ex.getResponseBodyAsString());
                throw new AiServiceException(
                        "A IA não conseguiu desenhar o look para essa requisição. Tente reformular as preferências.",
                        HttpStatus.BAD_GATEWAY, ex);

            } catch (HttpServerErrorException | ResourceAccessException ex) {
                ultimaFalhaTransitoria = ex;
                log.warn("Falha transitória ao gerar imagem (tentativa {}/{}): {}",
                        tentativa, MAX_TENTATIVAS, ex.getMessage());
                aguardarAntesDeRetentar();
            }
        }

        throw new AiServiceException(
                "O serviço de imagens está indisponível ou demorou demais para responder. Tente novamente em instantes.",
                HttpStatus.SERVICE_UNAVAILABLE, ultimaFalhaTransitoria);
    }

    /**
     * A resposta da Interactions API pode trazer a imagem em um campo de
     * conveniência (output_image) ou dentro dos steps/model_output. Tratamos
     * ambos para não depender de um único formato.
     */
    @SuppressWarnings("unchecked")
    private ImagemGerada extrairImagem(Map<String, Object> response) {
        try {
            Object wrapper = response.get("interaction");
            Map<String, Object> interaction = wrapper instanceof Map<?, ?> w
                    ? (Map<String, Object>) w
                    : response;

            ImagemGerada direta = lerBlocoImagem(interaction.get("output_image"));
            if (direta != null) {
                return direta;
            }

            if (interaction.get("steps") instanceof List<?> steps) {
                for (Object item : steps) {
                    if (!(item instanceof Map<?, ?> itemMap)) {
                        continue;
                    }

                    Object inner = itemMap.get("step");
                    Map<?, ?> step = inner instanceof Map<?, ?> s ? s : itemMap;

                    Object type = step.get("type");
                    if (type != null && !"model_output".equals(String.valueOf(type))) {
                        continue;
                    }

                    if (step.get("content") instanceof List<?> contents) {
                        for (Object parte : contents) {
                            if (parte instanceof Map<?, ?> parteMap) {
                                ImagemGerada img = lerParte(parteMap);
                                if (img != null) {
                                    return img;
                                }
                            }
                        }
                    }
                }
            }

            throw new IllegalStateException("nenhuma imagem encontrada na resposta");
        } catch (AiServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Resposta de imagem em formato inesperado. Campos recebidos: {}",
                    response.keySet(), ex);
            throw new AiServiceException(
                    "A IA retornou uma imagem vazia ou em formato inesperado. Tente novamente.",
                    HttpStatus.BAD_GATEWAY, ex);
        }
    }

    private ImagemGerada lerParte(Map<?, ?> parte) {
        Object inline = parte.get("inline_data");
        if (inline == null) {
            inline = parte.get("inlineData");
        }
        ImagemGerada img = lerBlocoImagem(inline);
        if (img != null) {
            return img;
        }
        return lerBlocoImagem(parte);
    }

    private ImagemGerada lerBlocoImagem(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }

        Object data = map.get("data");
        if (data == null) {
            data = map.get("base64");
        }
        if (!(data instanceof String base64) || base64.isBlank()) {
            return null;
        }

        Object mime = map.get("mime_type");
        if (mime == null) {
            mime = map.get("mimeType");
        }
        String mimeType = mime instanceof String s && !s.isBlank() ? s : MIME_TYPE;

        return new ImagemGerada(mimeType, base64);
    }

    private void aguardarAntesDeRetentar() {
        try {
            Thread.sleep(ESPERA_ENTRE_TENTATIVAS_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
