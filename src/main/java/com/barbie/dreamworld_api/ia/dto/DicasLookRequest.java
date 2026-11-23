package com.barbie.dreamworld_api.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Contexto enviado pelo frontend para a IA refinar um look que já foi gerado.
 * O look original não é recriado: as dicas são acrescentadas a ele.
 */
@Getter
@Setter
@Schema(description = "Contexto de um look já gerado para receber dicas aprimoradas")
public class DicasLookRequest {

    @Schema(description = "ID da Barbie do look original", example = "1")
    @NotNull(message = "O id da Barbie é obrigatório")
    private Long barbieId;

    @Schema(description = "ID do evento usado no look original, se houver", example = "2")
    private Long eventoId;

    @Schema(description = "Ocasião do look original", example = "Festa glamourosa no jardim")
    @Size(max = 300, message = "A descrição da ocasião deve ter no máximo 300 caracteres")
    private String ocasiao;

    @Schema(description = "Preferências informadas pelo usuário no look original")
    @Size(max = 300, message = "As preferências devem ter no máximo 300 caracteres")
    private String preferencias;

    @Schema(description = "Título do look já gerado", example = "Glamour Tropical em Dreamtopia")
    @NotBlank(message = "O título do look é obrigatório")
    @Size(max = 200)
    private String tituloLook;

    @Schema(description = "Resumo do look já gerado")
    @Size(max = 1000)
    private String resumo;

    @Schema(description = "Paleta de cores do look já gerado")
    private List<@Size(max = 80) String> paletaCores;

    @Schema(description = "Nomes dos itens sugeridos no look já gerado")
    private List<@Size(max = 150) String> itensSugeridos;

    @Schema(description = "Acessórios do acervo recomendados no look já gerado")
    private List<@Size(max = 150) String> acessoriosRecomendados;

    @Schema(description = "Foco opcional das dicas (ex.: calçado e acessórios)",
            example = "quero dicas de calçado e como prender o cabelo")
    @Size(max = 300, message = "O foco deve ter no máximo 300 caracteres")
    private String foco;
}
