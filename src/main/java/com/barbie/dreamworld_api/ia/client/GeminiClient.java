package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.exceptions.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Encapsula toda a comunicação com a API do Gemini (generateContent).
 *
 * Responsabilidades:
 *  - Montar o payload (contents + generationConfig com responseSchema, para
 *    forçar o modelo a responder em JSON estruturado e evitar parsing frágil).
 *  - Autenticar via header x-goog-api-key (padrão atual da API do Gemini).
 *  - Tratar timeout, 5xx transitório (com uma retentativa) e 429 (rate limit,
 *    sem retry — falha rápido e explica ao usuário).
 *  - Nunca deixar uma exceção "crua" do RestClient vazar para o controller:
 *    tudo vira AiServiceException, com uma mensagem amigável e um HttpStatus
 *    apropriado para a resposta ao usuário final.
 */
@Slf4j
@Component
public class GeminiClient {

    private static final int MAX_TENTATIVAS = 2;
    private static final long ESPERA_ENTRE_TENTATIVAS_MS = 500;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(RestClient geminiRestClient,
                         @Value("${gemini.api-key:}") String apiKey,
                         @Value("${gemini.model}") String model) {
        this.restClient = geminiRestClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Envia o prompt ao Gemini pedindo uma resposta em JSON aderente ao
     * responseSchema informado, e devolve o texto JSON bruto retornado.
     */
    public String generateJson(String prompt, Map<String, Object> responseSchema) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException(
                    "Assistente de Estilo não configurado: defina a variável de ambiente GEMINI_API_KEY.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.9,
                        "maxOutputTokens", 2048,
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema,
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );

        String path = "/v1beta/models/%s:generateContent".formatted(model);

        RuntimeException ultimaFalhaTransitoria = null;

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                Map<String, Object> response = restClient.post()
                        .uri(path)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                return extrairTexto(response);

            } catch (HttpClientErrorException.TooManyRequests ex) {
                // Rate limit: não adianta retentar imediatamente, falha rápido.
                throw new AiServiceException(
                        "O provedor de IA está recebendo muitas requisições agora. Aguarde alguns segundos e tente novamente.",
                        HttpStatus.TOO_MANY_REQUESTS, ex);

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
                log.error("Gemini rejeitou a chave de API: {}", ex.getResponseBodyAsString());
                throw new AiServiceException(
                        "Não foi possível autenticar com o provedor de IA. Verifique a GEMINI_API_KEY configurada.",
                        HttpStatus.SERVICE_UNAVAILABLE, ex);

            } catch (HttpClientErrorException ex) {
                // Outros 4xx (ex: prompt bloqueado por segurança, request malformado)
                log.error("Gemini respondeu {} para o path {}: {}",
                        ex.getStatusCode(), path, ex.getResponseBodyAsString());
                throw new AiServiceException(
                        "A IA não conseguiu gerar a sugestão para essa requisição. Tente reformular a ocasião ou as preferências.",
                        HttpStatus.BAD_GATEWAY, ex);

            } catch (HttpServerErrorException | ResourceAccessException ex) {
                // 5xx do lado do provedor ou timeout/conexão: vale tentar de novo.
                ultimaFalhaTransitoria = ex;
                log.warn("Falha transitória ao chamar o Gemini (tentativa {}/{}): {}",
                        tentativa, MAX_TENTATIVAS, ex.getMessage());
                aguardarAntesDeRetentar();
            }
        }

        throw new AiServiceException(
                "O serviço de IA está indisponível ou demorou demais para responder. Tente novamente em instantes.",
                HttpStatus.SERVICE_UNAVAILABLE, ultimaFalhaTransitoria);
    }

    @SuppressWarnings("unchecked")
    private String extrairTexto(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String texto = (String) parts.get(0).get("text");

            if (texto == null || texto.isBlank()) {
                throw new IllegalStateException("texto vazio");
            }
            return texto;
        } catch (Exception e) {
            log.error("Resposta do Gemini em formato inesperado: {}", response, e);
            throw new AiServiceException(
                    "A IA retornou uma resposta vazia ou em formato inesperado. Tente novamente.",
                    HttpStatus.BAD_GATEWAY, e);
        }
    }

    private void aguardarAntesDeRetentar() {
        try {
            Thread.sleep(ESPERA_ENTRE_TENTATIVAS_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
