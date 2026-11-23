package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerador de imagem alternativo e gratuito, usado como fallback quando o
 * Gemini Nano Banana não tem quota disponível (Free Tier = 0 imagens/dia).
 *
 * Devolvemos a URL pública em vez de descarregar a imagem no servidor: o
 * browser do utilizador carrega a imagem diretamente, evitando o limite de 1
 * imagem por 45s por IP partilhado do Render.
 *
 * Para não repetir sempre o mesmo desenho, cada chamada usa um seed novo e
 * acrescenta uma variação de estilo ao prompt. O botão "Outra imagem" força
 * uma variação diferente.
 */
@Slf4j
@Component
public class PollinationsImageClient {

    private static final String BASE_URL = "https://image.pollinations.ai/prompt/";
    private static final List<String> ESTILOS = List.of(
            "soft studio light, high detail",
            "glossy collectible doll render, dreamy lighting",
            "cinematic lighting, sharp details",
            "clean fashion editorial light, bright and crisp",
            "high detail illustration, gentle soft light"
    );

    public ImagemGerada gerarImagem(String prompt) {
        return gerarImagem(prompt, ThreadLocalRandom.current().nextInt(ESTILOS.size()));
    }

    /** Gera uma nova versão com uma variação de estilo diferente da anterior. */
    public ImagemGerada gerarNovaImagem(String prompt, int variacaoAnterior) {
        int novaVariacao = (variacaoAnterior + 1 + ThreadLocalRandom.current().nextInt(ESTILOS.size() - 1))
                % ESTILOS.size();
        return gerarImagem(prompt, novaVariacao);
    }

    private ImagemGerada gerarImagem(String prompt, int indiceEstilo) {
        String estilo = ESTILOS.get(indiceEstilo % ESTILOS.size());
        int seed = ThreadLocalRandom.current().nextInt(1_000_000);
        String promptFinal = prompt.replaceAll("[.\\s]+$", "")
                + ". Visual style variation: " + estilo + ".";
        String url = BASE_URL + URLEncoder.encode(promptFinal, StandardCharsets.UTF_8)
                + "?width=512&height=683&nologo=true&seed=" + seed
                + "&variation=" + indiceEstilo;

        log.info("Imagem alternativa gerada (estilo {}, seed {}).", indiceEstilo, seed);
        return ImagemGerada.fromUrl(url);
    }
}
