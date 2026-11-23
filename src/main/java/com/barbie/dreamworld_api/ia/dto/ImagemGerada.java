package com.barbie.dreamworld_api.ia.dto;

/**
 * Imagem disponibilizada ao frontend.
 *
 * O Gemini devolve o payload em base64. O gerador alternativo (Pollinations)
 * devolve uma URL pública; nesse caso o browser carrega a imagem diretamente,
 * o que evita o limite de 1 imagem por 45s por IP partilhado do servidor.
 */
public record ImagemGerada(String mimeType, String base64, String url) {

    public ImagemGerada(String mimeType, String base64) {
        this(mimeType, base64, null);
    }

    public static ImagemGerada fromUrl(String url) {
        return new ImagemGerada("image/jpeg", null, url);
    }

    public String toDataUrl() {
        if (url != null && !url.isBlank()) {
            return url;
        }
        return "data:" + mimeType + ";base64," + base64;
    }
}
