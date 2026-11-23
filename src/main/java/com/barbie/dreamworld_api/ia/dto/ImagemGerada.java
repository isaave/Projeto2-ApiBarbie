package com.barbie.dreamworld_api.ia.dto;

/**
 * Imagem disponibilizada ao frontend.
 *
 * Pode vir do Gemini (base64), de uma URL pública ou, no fallback atual,
 * de uma ilustração vetorial gerada pelo próprio backend a partir do look.
 */
public record ImagemGerada(String mimeType, String base64, String url) {

    public ImagemGerada(String mimeType, String base64) {
        this(mimeType, base64, null);
    }

    public static ImagemGerada fromUrl(String url) {
        return new ImagemGerada("image/jpeg", null, url);
    }

    public static ImagemGerada fromSvg(String svg) {
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new ImagemGerada("image/svg+xml", base64, null);
    }

    public String toDataUrl() {
        if (url != null && !url.isBlank()) {
            return url;
        }
        return "data:" + mimeType + ";base64," + base64;
    }
}
