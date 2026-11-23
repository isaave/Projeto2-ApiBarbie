package com.barbie.dreamworld_api.ia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuração dos clientes HTTP usados para falar com a API do Gemini.
 *
 * São dois clientes porque a geração de imagem (Nano Banana) costuma demorar
 * bem mais que a geração de texto. Manter timeouts separados evita que um
 * timeout curto de texto derrube a imagem (ou vice-versa).
 */
@Configuration
public class GeminiConfig {

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${gemini.image-timeout-ms:90000}")
    private int imageTimeoutMs;

    @Bean
    public RestClient geminiRestClient() {
        return buildClient(timeoutMs);
    }

    @Bean
    public RestClient geminiImageRestClient() {
        return buildClient(imageTimeoutMs);
    }

    private RestClient buildClient(int timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
