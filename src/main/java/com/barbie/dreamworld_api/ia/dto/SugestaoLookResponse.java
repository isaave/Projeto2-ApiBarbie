package com.barbie.dreamworld_api.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Sugestão de look gerada por IA generativa para uma Barbie, combinando
 * dados reais do acervo dela com o conteúdo criado pelo modelo.
 */
@Getter
@Setter
@Builder
@Schema(description = "Sugestão de look gerada por IA para uma Barbie")
public class SugestaoLookResponse {

    @Schema(description = "ID da Barbie para quem a sugestão foi gerada", example = "1")
    private Long barbieId;

    @Schema(description = "Nome da Barbie", example = "Barbie Cientista")
    private String nomeBarbie;

    @Schema(description = "Ocasião considerada na geração do look")
    private String ocasiao;

    @Schema(description = "Título curto do look sugerido pela IA", example = "Look Laboratório Glamouroso")
    private String tituloLook;

    @Schema(description = "Resumo explicando a ideia geral do look")
    private String resumo;

    @Schema(description = "Paleta de cores sugerida")
    private List<String> paletaCores;

    @Schema(description = "Itens sugeridos para compor o look")
    private List<ItemSugerido> itensSugeridos;

    @Schema(description = "Acessórios que a Barbie já possui e que a IA recomenda usar nesse look")
    private List<String> acessoriosDoAcervoRecomendados;

    @Schema(description = "Imagem da Barbie vestida com o look gerado. Use o campo 'dataUrl' diretamente no atributo src de um <img>.")
    private ImagemLook imagem;

    @Getter
    @Setter
    @Builder
    @Schema(description = "Imagem gerada pela IA para o look")
    public static class ImagemLook {

        @Schema(description = "URL completa pronta para uso: data:image/png;base64,...")
        private String dataUrl;

        @Schema(description = "Tipo MIME da imagem", example = "image/png")
        private String mimeType;

        @Schema(description = "Conteúdo da imagem em base64 (sem o prefixo data:)")
        private String base64;
    }

    @Getter
    @Setter
    @Builder
    @Schema(description = "Item de vestuário ou acessório sugerido pela IA")
    public static class ItemSugerido {

        @Schema(description = "Nome do item sugerido", example = "Sandália prateada com salto bloco")
        private String nome;

        @Schema(description = "Categoria do item", example = "Calçado")
        private String categoria;

        @Schema(description = "Motivo da IA ter sugerido esse item")
        private String motivo;
    }
}
