package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerador de imagem alternativo e gratuito, usado como fallback quando o
 * Gemini Nano Banana não tem quota disponível (Free Tier = 0 imagens/dia).
 *
 * Em vez de descarregar a imagem no servidor — o que esgota o limite de 1
 * imagem por 45s por IP partilhado do Render — devolvemos a URL pública.
 * O browser do utilizador carrega a imagem diretamente, com o mesmo prompt
 * montado a partir do look gerado pelo Gemini.
 */
@Slf4j
@Component
public class PollinationsImageClient {

    private static final String BASE_URL = "https://image.pollinations.ai/prompt/";

    public ImagemGerada gerarImagem(String prompt) {
        String encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        int seed = ThreadLocalRandom.current().nextInt(1_000_000);
        String url = BASE_URL + encoded
                + "?width=512&height=683&nologo=true&seed=" + seed;
        log.info("Imagem alternativa disponibilizada por URL.");
        return ImagemGerada.fromUrl(url);
    }
}
