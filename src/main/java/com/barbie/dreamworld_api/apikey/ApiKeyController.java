package com.barbie.dreamworld_api.apikey;

import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/auth/api-keys")
@RequiredArgsConstructor
@Tag(
        name = "Autenticação — API Keys",
        description = "Geração e gerenciamento das chaves de acesso da Barbie Security para proteger os endpoints da Dreamhouse."
)
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;

    @Operation(
            summary = "Gerar nova chave de API",
            description = """
                    Gera uma nova chave de acesso para utilizar nos endpoints protegidos da API.

                    Use o valor retornado no campo `apiKey` dentro do header `X-API-Key`.

                    Níveis de acesso:
                    READ  → permite apenas consultas GET
                    WRITE → permite consultas e alterações
                    ADMIN → permite acesso completo e revogação de chaves
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Nova chave da Barbie Security gerada com sucesso.",
                    headers = @Header(
                            name = "Location",
                            description = "URI da nova chave criada"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para gerar a chave são inválidos. Verifique owner ou nível de acesso.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "O recurso necessário para gerar a chave não foi encontrado no sistema da Barbie Security.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito ao gerar chave. Já pode existir uma chave ativa equivalente para este responsável.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas tentativas de geração de chaves. A Barbie Security bloqueou temporariamente novas solicitações."
            )
    })
    @PostMapping
    public ResponseEntity<ApiKeyResponse> generate(@Valid @RequestBody ApiKeyRequest req) {
        ApiKey entity = new ApiKey();
        entity.setOwner(req.getOwner());

        if (req.getAccessLevel() != null) {
            entity.setAccessLevel(req.getAccessLevel());
        }

        ApiKey saved = apiKeyRepository.save(entity);

        return ResponseEntity
                .created(URI.create("/api/auth/api-keys/" + saved.getId()))
                .body(toResponse(saved));
    }

    @Operation(
            summary = "Listar todas as chaves de API",
            description = "Retorna todas as chaves cadastradas na Barbie Security, incluindo chaves ativas e inativas."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de chaves da Barbie Security retornada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida pode ser exigida para consultar chaves protegidas."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma chave foi encontrada no sistema da Barbie Security.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas às chaves em pouco tempo. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listAll() {
        return ResponseEntity.ok(
                apiKeyRepository.findAll().stream().map(this::toResponse).toList()
        );
    }

    @Operation(
            summary = "Buscar chave de API por ID",
            description = "Consulta uma chave específica cadastrada na Barbie Security pelo seu identificador."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Chave da Barbie Security encontrada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para buscar a chave é inválido."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida pode ser necessária para consultar esta chave."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma chave foi encontrada com esse ID.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas buscas por chaves em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> getById(
            @Parameter(description = "ID da chave de API", example = "1")
            @PathVariable Long id
    ) {
        return apiKeyRepository.findById(id)
                .map(k -> ResponseEntity.ok(toResponse(k)))
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));
    }

    @Operation(
            summary = "Revogar uma chave de API",
            description = "Desativa uma chave de API. Depois da revogação, requisições usando essa chave passarão a receber 401 Unauthorized."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Chave revogada com sucesso pela Barbie Security."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para revogar a chave é inválido."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso negado. Uma chave ADMIN válida pode ser necessária para revogar chaves."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma chave foi encontrada com esse ID para revogação.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito ao revogar a chave. Ela pode já estar inativa ou protegida por regra administrativa.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas tentativas de revogação em sequência. A Barbie Security bloqueou temporariamente novas alterações."
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> revoke(
            @Parameter(description = "ID da chave a revogar", example = "1")
            @PathVariable Long id
    ) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));

        key.setActive(false);

        return ResponseEntity.ok(toResponse(apiKeyRepository.save(key)));
    }

    private ApiKeyResponse toResponse(ApiKey k) {
        return new ApiKeyResponse(
                k.getId(),
                k.getKeyValue(),
                k.getOwner(),
                k.getAccessLevel(),
                k.isActive(),
                k.getCreatedAt()
        );
    }
}