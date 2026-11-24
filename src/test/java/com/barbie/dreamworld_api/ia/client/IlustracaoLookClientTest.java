package com.barbie.dreamworld_api.ia.client;

import com.barbie.dreamworld_api.ia.dto.ImagemGerada;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IlustracaoLookClientTest {

    private final IlustracaoLookClient client = new IlustracaoLookClient();

    private SugestaoLookResponse look() {
        return SugestaoLookResponse.builder()
                .tituloLook("Look floral rosa")
                .resumo("Vestido floral rosa com óculos de sol e bolsa branca")
                .promptImagem("pink floral dress, white sunglasses, white handbag, garden")
                .paletaCores(List.of("rosa", "branco"))
                .itensSugeridos(List.of(SugestaoLookResponse.ItemSugerido.builder()
                        .nome("Vestido floral rosa")
                        .categoria("Vestuário")
                        .motivo("Combina com a ocasião")
                        .build()))
                .acessoriosDoAcervoRecomendados(List.of("Óculos de sol", "Bolsa branca"))
                .build();
    }

    @Test
    void geraSvgValidoComDataUrl() {
        ImagemGerada imagem = client.gerarImagem("prompt", look());

        assertEquals("image/svg+xml", imagem.mimeType());
        assertTrue(imagem.toDataUrl().startsWith("data:image/svg+xml;base64,"));

        String svg = new String(Base64.getDecoder().decode(imagem.base64()), StandardCharsets.UTF_8);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("</svg>"));
        // A cor primária do look aparece na ilustração.
        assertTrue(svg.contains("#ff69b4"));
        // Traços reconhecíveis da boneca: cabelo loiro, olhos grandes, batom e salto.
        assertTrue(svg.contains("id=\"cabelo\""));
        assertTrue(svg.contains("url(#cabelo)"));
        assertTrue(svg.contains("#fff3c4"));
        assertTrue(svg.contains("cx=\"228\" cy=\"230\""));
        assertTrue(svg.contains("url(#lips)"));
        assertTrue(svg.contains("M220 606"));
        // Nunca desenha texto, logos ou marcas d'água.
        assertFalse(svg.contains("<text"));
        assertFalse(svg.toLowerCase().contains("watermark"));
    }

    @Test
    void outraVariacaoMudaAImagemMasMantemOCorpoDoLook() {
        SugestaoLookResponse look = look();

        String primeira = client.gerarImagem("prompt", look).toDataUrl();
        String segunda = client.gerarNovaImagem("prompt", 1, look).toDataUrl();
        String terceira = client.gerarNovaImagem("prompt", Integer.MIN_VALUE, look).toDataUrl();

        assertNotEquals(primeira, segunda);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(
                terceira.substring(terceira.indexOf(',') + 1)));
    }

    @Test
    void aceitaLookNuloSemQuebrar() {
        assertDoesNotThrow(() -> client.gerarImagem("vestido azul", null));
    }
}
