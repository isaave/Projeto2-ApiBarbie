package com.barbie.dreamworld_api.apikey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Dados para geração de uma nova chave de API")
public class ApiKeyRequest {

    @NotBlank(message = "O nome do proprietário é obrigatório")
    @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres")
    @Schema(description = "Nome do proprietário da chave", example = "App Frontend")
    private String owner;

    @Schema(description = "Nível de acesso: READ ou WRITE (padrão: WRITE). ADMIN não é permitido nesta rota pública.", example = "WRITE")
    private ApiKey.AccessLevel accessLevel = ApiKey.AccessLevel.WRITE;
}
