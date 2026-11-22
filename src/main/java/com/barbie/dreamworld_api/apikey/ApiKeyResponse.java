package com.barbie.dreamworld_api.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Schema(description = "Resposta com a chave de API gerada")
public class ApiKeyResponse {

    @Schema(description = "ID da chave", example = "1")
    private Long id;

    @Schema(description = "Chave de API — guarde em local seguro",
            example = "a3f1c2d4-e5b6-7890-abcd-ef1234567890")
    private String apiKey;

    @Schema(description = "Proprietário da chave", example = "App Frontend")
    private String owner;

    @Schema(description = "Nível de acesso", example = "WRITE")
    private ApiKey.AccessLevel accessLevel;

    @Schema(description = "Se a chave está ativa", example = "true")
    private boolean active;

    @Schema(description = "Data de criação")
    private LocalDateTime createdAt;
}
