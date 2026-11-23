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
@Schema(
        description = "Sugestão de look gerada por IA para uma Barbie",
        example = """
                {
                  "barbieId": 1,
                  "nomeBarbie": "Barbie Cientista",
                  "ocasiao": "Festa glamourosa no jardim",
                  "tituloLook": "Look Laboratório Glamouroso",
                  "resumo": "Um look que mistura ciência e elegância, com tons metálicos e um toque futurista.",
                  "paletaCores": ["prateado", "lilás", "branco"],
                  "itensSugeridos": [
                    {
                      "nome": "Jaleco branco ajustado",
                      "categoria": "Vestuário",
                      "motivo": "Reforça a identidade científica com um toque moderno."
                    },
                    {
                      "nome": "Sandália prateada com salto bloco",
                      "categoria": "Calçado",
                      "motivo": "Adiciona brilho sem perder a praticidade."
                    }
                  ],
                  "acessoriosDoAcervoRecomendados": ["Óculos de proteção futuristas", "Bolsa holográfica"],
                  "imagem": {
                    "dataUrl": "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmci...",
                    "mimeType": "image/svg+xml",
                    "base64": "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmci..."
                  }
                }
                """
)
public class SugestaoLookResponse {

    @Schema(description = "ID da Barbie para quem a sugestão foi gerada", example = "1")
    private Long barbieId;

    @Schema(description = "Nome da Barbie", example = "Barbie Cientista")
    private String nomeBarbie;

    @Schema(description = "Ocasião considerada na geração do look", example = "Festa glamourosa no jardim")
    private String ocasiao;

    @Schema(description = "Título curto do look sugerido pela IA", example = "Look Laboratório Glamouroso")
    private String tituloLook;

    @Schema(
            description = "Resumo explicando a ideia geral do look",
            example = "Um look que mistura ciência e elegância, com tons metálicos e um toque futurista."
    )
    private String resumo;

    @Schema(description = "Paleta de cores sugerida", example = "[\"prateado\", \"lilás\", \"branco\"]")
    private List<String> paletaCores;

    @Schema(description = "Itens sugeridos para compor o look")
    private List<ItemSugerido> itensSugeridos;

    @Schema(
            description = "Acessórios que a Barbie já possui e que a IA recomenda usar nesse look",
            example = "[\"Óculos de proteção futuristas\", \"Bolsa holográfica\"]"
    )
    private List<String> acessoriosDoAcervoRecomendados;

    @Schema(
            description = "Imagem da Barbie vestida com o look gerado. Use o campo 'dataUrl' diretamente no atributo src de um <img>.",
            example = """
                    {
                      "dataUrl": "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmci...",
                      "mimeType": "image/svg+xml",
                      "base64": "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmci..."
                    }
                    """
    )
    private ImagemLook imagem;

    @Schema(
            description = "Aviso quando o texto foi gerado mas a imagem não pôde ser criada (ex.: quota do provedor).",
            example = "O look foi gerado, mas a imagem não pôde ser criada: limite de requisições atingido."
    )
    private String avisoImagem;

    @Schema(
            description = "Aviso de que o Gemini estava indisponível e o look foi montado localmente a partir do acervo real.",
            example = "A IA generativa estava temporariamente indisponível (cota ou conexão). Este look foi montado automaticamente a partir do acervo real da Barbie."
    )
    private String aviso;

    @Schema(description = "Dicas aprimoradas da estilista para refinar este look na mesma ocasião")
    private List<DicasLookResponse.Dica> dicas;

    @Schema(
            description = "Prompt visual curto em inglês, gerado pela IA, descrevendo exatamente as peças, cores e cenário do look. É usado para gerar a imagem.",
            example = "Barbie fashion doll wearing a floral midi dress in pink and white, white cat-eye sunglasses, pink quilted handbag, garden party, full body"
    )
    private String promptImagem;

    @Getter
    @Setter
    @Builder
    @Schema(description = "Imagem gerada pela IA para o look")
    public static class ImagemLook {

        @Schema(
                description = "URL completa pronta para uso: data:image/svg+xml;base64,...",
                example = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmci..."
        )
        private String dataUrl;

        @Schema(description = "Tipo MIME da imagem", example = "image/svg+xml")
        private String mimeType;

        @Schema(
                description = "Conteúdo da imagem em base64 (sem o prefixo data:)",
                example = "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmci..."
        )
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

        @Schema(
                description = "Motivo da IA ter sugerido esse item",
                example = "Adiciona brilho sem perder a praticidade."
        )
        private String motivo;
    }
}
