package com.barbie.dreamworld_api.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Dicas práticas da IA para aperfeiçoar um look que já foi sugerido. */
@Getter
@Setter
@Builder
@Schema(description = "Dicas aprimoradas para um look gerado anteriormente", example = """
        {
          "titulo": "Ajustes finais para o Glamour Tropical",
          "resumo": "O look já está coerente; vale reforçar a silhueta e o brilho na faixa etária da ocasião.",
          "dicas": [
            {
              "categoria": "Caimento",
              "titulo": "Marque a cintura",
              "descricao": "Use um cinto fino da mesma paleta para definir a silhueta sem competir com o estampado."
            }
          ],
          "toqueFinal": "Finalize com um sorriso e uma pose leve de braços abertos."
        }
        """)
public class DicasLookResponse {

    @Schema(description = "Título das dicas aprimoradas")
    private String titulo;

    @Schema(description = "Resumo das melhorias sugeridas")
    private String resumo;

    @Schema(description = "Dicas práticas e específicas para o look")
    private List<Dica> dicas;

    @Schema(description = "Toque final sugerido pela estilista")
    private String toqueFinal;

    @Schema(
            description = "Aviso de que o Gemini estava indisponível e as dicas foram montadas localmente a partir do acervo real.",
            example = "A IA generativa estava temporariamente indisponível (cota ou conexão). Este look foi montado automaticamente a partir do acervo real da Barbie."
    )
    private String aviso;

    @Getter
    @Setter
    @Builder
    @Schema(description = "Uma dica prática de refinamento")
    public static class Dica {

        @Schema(description = "Categoria da dica", example = "Calçado")
        private String categoria;

        @Schema(description = "Título curto da dica", example = "Prefira salto bloco")
        private String titulo;

        @Schema(description = "Explicação objetiva da dica")
        private String descricao;
    }
}
