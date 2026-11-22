package com.barbie.dreamworld_api.versioning;

import com.barbie.dreamworld_api.assemblers.BarbieModelAssembler;
import com.barbie.dreamworld_api.dto.response.BarbieResponse;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.model.Barbie;
import com.barbie.dreamworld_api.repository.BarbieRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Demonstração de versionamento via header X-API-Version.
 *
 * v1 → resposta simplificada (id, nome, colecao)
 * v2 → resposta completa com HATEOAS
 *
 * Rotas disponíveis:
 *   GET /api/v1/barbies        → lista simplificada
 *   GET /api/v1/barbies/{id}   → busca simplificada
 *   GET /api/v2/barbies        → lista completa com HATEOAS
 *   GET /api/v2/barbies/{id}   → busca completa com HATEOAS
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Barbies — Versionamento",
     description = "Endpoints versionados via X-API-Version · v1 simplificado · v2 completo com HATEOAS")
public class BarbieVersionedController {

    private final BarbieRepository     barbieRepository;
    private final BarbieModelAssembler assembler;

    // ── helpers ───────────────────────────────────────────────────────────────

    private BarbieResponseV1 toV1(Barbie b) {
        return new BarbieResponseV1(b.getId(), b.getNome(), b.getColecao());
    }

    private BarbieResponse toV2(Barbie b) {
        return new BarbieResponse(
                b.getId(), b.getNome(), b.getColecao(),
                b.getProfissao()  != null ? b.getProfissao().getNomeProfissao() : null,
                b.getProfissao()  != null ? b.getProfissao().getAreaAtuacao()   : null,
                b.getDreamhouse() != null ? b.getDreamhouse().getNomeCasa()     : null,
                b.getAcessorios().size(),
                b.getEventos().size()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // v1
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "[v1] Listar Barbies — resposta simplificada",
        description = "**Versão 1**: retorna apenas `id`, `nome` e `colecao`.",
        parameters  = @Parameter(name = "X-API-Version", in = ParameterIn.HEADER,
                                 description = "Versão da API", example = "v1")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "429", description = "Muitas requisições",
                     content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/api/v1/barbies")
    public ResponseEntity<Page<BarbieResponseV1>> listV1(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok()
                .header("X-API-Version", "v1")
                .body(barbieRepository.findAll(pageable).map(this::toV1));
    }

    @Operation(
        summary     = "[v1] Buscar Barbie por ID — resposta simplificada",
        description = "**Versão 1**: retorna apenas `id`, `nome` e `colecao`."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Barbie encontrada"),
        @ApiResponse(responseCode = "404", description = "Barbie não encontrada",
                     content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/api/v1/barbies/{id}")
    public ResponseEntity<BarbieResponseV1> getByIdV1(@PathVariable Long id) {
        Barbie b = barbieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", id));
        return ResponseEntity.ok().header("X-API-Version", "v1").body(toV1(b));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // v2
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "[v2] Listar Barbies — resposta completa com HATEOAS",
        description = "**Versão 2**: retorna todos os campos + links HATEOAS."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "429", description = "Muitas requisições",
                     content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/api/v2/barbies")
    public ResponseEntity<PagedModel<BarbieResponse>> listV2(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<BarbieResponse> pagedAssembler) {
        Page<BarbieResponse> page = barbieRepository.findAll(pageable).map(this::toV2);
        return ResponseEntity.ok()
                .header("X-API-Version", "v2")
                .body(pagedAssembler.toModel(page, assembler));
    }

    @Operation(
        summary     = "[v2] Buscar Barbie por ID — resposta completa com HATEOAS",
        description = "**Versão 2**: retorna todos os campos + links HATEOAS."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Barbie encontrada"),
        @ApiResponse(responseCode = "404", description = "Barbie não encontrada",
                     content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/api/v2/barbies/{id}")
    public ResponseEntity<BarbieResponse> getByIdV2(@PathVariable Long id) {
        Barbie b = barbieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", id));
        return ResponseEntity.ok()
                .header("X-API-Version", "v2")
                .body(assembler.toModel(toV2(b)));
    }
}
