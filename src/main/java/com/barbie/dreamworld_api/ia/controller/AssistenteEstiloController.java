package com.barbie.dreamworld_api.ia.controller;

import com.barbie.dreamworld_api.ia.dto.DicasLookRequest;
import com.barbie.dreamworld_api.ia.dto.DicasLookResponse;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookRequest;
import com.barbie.dreamworld_api.ia.dto.SugestaoLookResponse;
import com.barbie.dreamworld_api.ia.service.AssistenteEstiloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Assistente de Estilo (IA)",
        description = "Sugestões de look geradas por IA generativa (Gemini) a partir dos dados reais das Barbies cadastradas")
@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class AssistenteEstiloController {

    private final AssistenteEstiloService assistenteEstiloService;

    @Operation(
            summary = "Gerar sugestão de look com IA",
            description = """
                    Usa os dados reais da Barbie (profissão, coleção, dreamhouse e acessórios
                    já cadastrados) combinados com a ocasião informada (um evento já cadastrado
                    OU uma descrição livre) para gerar, via IA generativa, uma sugestão de look
                    estruturada em JSON e, em seguida, gera uma imagem da Barbie vestida com
                    esse look usando o modelo Nano Banana. A imagem vem no campo 'imagem.dataUrl'
                    (formato data:image/...;base64,...), pronta para usar no src de um <img>.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sugestão gerada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição inválido",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Barbie ou evento não encontrado",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "422", description = "Nem 'eventoId' nem 'ocasiao' foram informados",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "429", description = "Limite de requisições do provedor de IA atingido",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "A IA retornou uma resposta inválida ou foi rejeitada",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível, sem chave configurada ou timeout",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Acesso negado pela Barbie Security. Uma X-API-Key válida é necessária.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/assistente-estilo")
    public ResponseEntity<SugestaoLookResponse> gerarSugestao(
            @Valid @RequestBody SugestaoLookRequest request) {
        return ResponseEntity.ok(assistenteEstiloService.gerarSugestao(request));
    }

    @Operation(
            summary = "Receber dicas aprimoradas para um look já gerado",
            description = """
                    Envia o look gerado anteriormente como contexto e devolve dicas práticas
                    da IA para refiná-lo na mesma ocasião (caimento, cores, calçado, acessórios,
                    cabelo e toque final). O look original não é recriado.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dicas geradas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição inválido",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Barbie ou evento não encontrado",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "429", description = "Limite de requisições do provedor de IA atingido",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "A IA retornou uma resposta inválida",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível ou sem chave",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Acesso negado pela Barbie Security. Uma X-API-Key válida é necessária.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/assistente-estilo/dicas")
    public ResponseEntity<DicasLookResponse> gerarDicas(
            @Valid @RequestBody DicasLookRequest request) {
        return ResponseEntity.ok(assistenteEstiloService.gerarDicas(request));
    }

    @Operation(
            summary = "Gerar outra imagem para o mesmo look",
            description = """
                    Mantém o look em texto e devolve uma nova variação determinística da
                    ilustração vetorial da Barbie: pose, penteado e cenário mudam, mas as
                    cores, peças e acessórios continuam sendo exatamente os do look.
                    Não consome o Gemini e não depende de serviço externo.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nova ilustração gerada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição inválido",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "X-API-Key ausente ou inválida",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Chave sem permissão de escrita",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Barbie ou evento não encontrado",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "429", description = "Limite de requisições atingido",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/assistente-estilo/regerar-imagem")
    public ResponseEntity<SugestaoLookResponse.ImagemLook> gerarNovaImagem(
            @Valid @RequestBody DicasLookRequest request) {
        return ResponseEntity.ok(assistenteEstiloService.gerarNovaImagem(request));
    }
}