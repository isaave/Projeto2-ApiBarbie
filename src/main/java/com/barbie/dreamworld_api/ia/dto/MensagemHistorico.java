package com.barbie.dreamworld_api.ia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma mensagem recente da conversa com a estilista.
 *
 * O histórico é controlado pelo cliente e limitado pelo servidor (no máximo
 * 6 mensagens de até 400 caracteres) para não estourar a janela de contexto
 * da LLM.
 */
@Getter
@Setter
@Schema(description = "Mensagem recente do chat, usada como contexto controlado")
public class MensagemHistorico {

    @Schema(description = "Quem falou: 'user' ou 'ia'", example = "user")
    @NotBlank(message = "O papel da mensagem é obrigatório")
    @Pattern(regexp = "^(user|ia)$", message = "O papel deve ser 'user' ou 'ia'")
    private String papel;

    @Schema(description = "Texto da mensagem (máximo 400 caracteres)")
    @NotBlank(message = "O texto da mensagem é obrigatório")
    @Size(max = 400, message = "Cada mensagem do histórico deve ter no máximo 400 caracteres")
    private String texto;
}
