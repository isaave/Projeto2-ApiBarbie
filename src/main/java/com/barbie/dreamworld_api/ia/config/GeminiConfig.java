package com.barbie.dreamworld_api.ia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP usados pelos geradores de IA.
 *
 * São três clientes porque texto, imagem do Gemini e imagem externa têm
 * tempos de resposta bem diferentes. Manter timeouts separados evita que um
 * timeout curto derrube a geração de imagem (ou vice-versa).
 */
@Configuration
public class GeminiConfig {

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${gemini.image-timeout-ms:90000}")
    private int imageTimeoutMs;

    @Value("${image.pollinations.base-url:https://image.pollinations.ai}")
    private String pollinationsBaseUrl;

    @Value("${image.pollinations.timeout-ms:90000}")
    private int pollinationsTimeoutMs;

    @Bean
    public RestClient geminiRestClient() {
        return buildClient(baseUrl, timeoutMs);
    }

    @Bean
    public RestClient geminiImageRestClient() {
        return buildClient(baseUrl, imageTimeoutMs);
    }

    @Bean
    public RestClient pollinationsRestClient() {
        return buildClient(pollinationsBaseUrl, pollinationsTimeoutMs);
    }

    private RestClient buildClient(String urlBase, int timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(urlBase)
                .requestFactory(requestFactory)
                .build();
    }
}
