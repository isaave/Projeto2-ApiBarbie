package com.barbie.dreamworld_api.ia.dto;

/**
 * Imagem devolvida pelo modelo Nano Banana.
 *
 * O payload bruto da API vem em base64; aqui ele já sai pronto para ser
 * usado diretamente no atributo `src` de um <img> no frontend.
 */
public record ImagemGerada(String mimeType, String base64) {

    public String toDataUrl() {
        return "data:" + mimeType + ";base64," + base64;
    }
}
