package com.barbie.dreamworld_api.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Dados enviados pelo cliente para gerar, via IA generativa, uma sugestão
 * de look para uma Barbie já cadastrada.
 *
 * Regra de negócio (validada no service, não aqui): é obrigatório informar
 * eventoId OU ocasiao — os dois juntos também são aceitos (eventoId prevalece).
 */
@Getter
@Setter
@Schema(description = "Dados para gerar uma sugestão de look com IA para uma Barbie")
public class SugestaoLookRequest {

    @Schema(description = "ID da Barbie para quem o look será sugerido", example = "1")
    @NotNull(message = "O id da Barbie é obrigatório")
    private Long barbieId;

    @Schema(description = "ID de um evento já cadastrado para usar como ocasião (opcional se 'ocasiao' for informado)",
            example = "2")
    private Long eventoId;

    @Schema(description = "Descrição livre da ocasião, usada quando eventoId não é informado",
            example = "Festa de aniversário na praia, ao entardecer")
    @Size(max = 300, message = "A descrição da ocasião deve ter no máximo 300 caracteres")
    private String ocasiao;

    @Schema(description = "Preferências adicionais de estilo (opcional)",
            example = "Gosto de tons pastel e prefiro evitar saltos muito altos")
    @Size(max = 300, message = "As preferências devem ter no máximo 300 caracteres")
    private String preferencias;
}
