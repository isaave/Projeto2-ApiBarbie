package com.barbie.dreamworld_api.controllers;

import com.barbie.dreamworld_api.assemblers.ProfissaoModelAssembler;
import com.barbie.dreamworld_api.dto.request.ProfissaoRequest;
import com.barbie.dreamworld_api.dto.response.ProfissaoResponse;
import com.barbie.dreamworld_api.exceptions.BusinessException;
import com.barbie.dreamworld_api.exceptions.ConflictException;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.model.Profissao;
import com.barbie.dreamworld_api.repository.ProfissaoRepository;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(
        name = "Profissões",
        description = "Gerenciamento das carreiras das Barbies"
)
@RestController
@RequestMapping("/api/profissoes")
@RequiredArgsConstructor
public class ProfissaoController {

    private final ProfissaoRepository profissaoRepository;
    private final ProfissaoModelAssembler assembler;

    private record ProfissaoFingerprint(String nomeProfissao, String areaAtuacao) {
    }

    private record IdempotentProfissaoResponse(ProfissaoFingerprint fingerprint, ProfissaoResponse body, URI location) {
    }

    private final ConcurrentHashMap<String, IdempotentProfissaoResponse> idempotencyCache = new ConcurrentHashMap<>();
    private final Object idempotencyLock = new Object();

    @Operation(summary = "Listar todas as profissões")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "As carreiras do universo Barbie foram carregadas com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os parâmetros enviados para listar as profissões são inválidos."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas às carreiras da Barbie em pouco tempo. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping
    public ResponseEntity<PagedModel<ProfissaoResponse>> listAll(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<ProfissaoResponse> pagedAssembler
    ) {
        Page<ProfissaoResponse> page = profissaoRepository.findAll(pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Buscar profissão por ID")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profissão encontrada com sucesso no catálogo de carreiras da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para buscar a profissão é inválido."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma profissão foi encontrada com esse ID no universo Barbie.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas buscas por profissões em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProfissaoResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(toResponse(findOrThrow(id))));
    }

    @Operation(summary = "Buscar profissões por nome")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Busca realizada com sucesso entre as profissões da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O nome informado para buscar profissões é inválido."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas pesquisas por profissões em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/search")
    public ResponseEntity<PagedModel<ProfissaoResponse>> search(
            @RequestParam String nome,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<ProfissaoResponse> pagedAssembler
    ) {
        Page<ProfissaoResponse> page = profissaoRepository.searchByNome(nome, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Listar profissões por área de atuação")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "As profissões da área escolhida foram listadas com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A área de atuação informada é inválida."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas por área de atuação em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/area/{area}")
    public ResponseEntity<PagedModel<ProfissaoResponse>> listByArea(
            @PathVariable String area,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<ProfissaoResponse> pagedAssembler
    ) {
        Page<ProfissaoResponse> page = profissaoRepository.findByAreaAtuacaoIgnoreCase(area, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Listar profissões mais populares")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "As profissões mais populares do universo Barbie foram listadas com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A requisição para listar profissões populares é inválida."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas às profissões populares. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/mais-populares")
    public ResponseEntity<PagedModel<ProfissaoResponse>> listMaisPopulares(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<ProfissaoResponse> pagedAssembler
    ) {
        Page<ProfissaoResponse> page = profissaoRepository.findMaisPopulares(pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Cadastrar nova profissão")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Nova profissão adicionada com sucesso ao universo Barbie.",
                    headers = @Header(
                            name = "Location",
                            description = "URI da nova profissão criada"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para criar a profissão são inválidos. Verifique nome, descrição, área de atuação ou Idempotency-Key.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso negado pela Barbie Security. Uma X-API-Key válida é necessária para cadastrar profissões."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito detectado no catálogo de carreiras. Já existe uma profissão com esse nome ou a Idempotency-Key foi reutilizada incorretamente.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas tentativas de cadastro em sequência. O catálogo de profissões atingiu o limite temporário de requisições."
            )
    })
    @PostMapping
    public ResponseEntity<ProfissaoResponse> create(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @Parameter(description = "Chave única para garantir idempotência da criação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody ProfissaoRequest req
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("O header Idempotency-Key é obrigatório e não pode ser vazio.");
        }

        ProfissaoFingerprint fingerprint = new ProfissaoFingerprint(
                req.getNomeProfissao(),
                req.getAreaAtuacao()
        );

        synchronized (idempotencyLock) {
            IdempotentProfissaoResponse cached = idempotencyCache.get(idempotencyKey);

            if (cached != null) {
                if (!cached.fingerprint().equals(fingerprint)) {
                    throw new ConflictException("Idempotency-Key já utilizada com um payload diferente.");
                }

                return ResponseEntity.created(cached.location()).body(cached.body());
            }

            if (profissaoRepository.existsByNomeProfissaoIgnoreCase(req.getNomeProfissao())) {
                throw new ConflictException("Já existe uma profissão com o nome: " + req.getNomeProfissao());
            }

            Profissao p = new Profissao();
            p.setNomeProfissao(req.getNomeProfissao());
            p.setDescricao(req.getDescricao());
            p.setAreaAtuacao(req.getAreaAtuacao());

            Profissao saved = profissaoRepository.save(p);

            URI location = linkTo(methodOn(ProfissaoController.class).getById(saved.getId())).toUri();
            ProfissaoResponse body = assembler.toModel(toResponse(saved));

            idempotencyCache.put(
                    idempotencyKey,
                    new IdempotentProfissaoResponse(fingerprint, body, location)
            );

            return ResponseEntity.created(location).body(body);
        }
    }

    @Operation(summary = "Atualizar profissão")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A profissão foi atualizada com sucesso no catálogo de carreiras da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para atualizar a profissão são inválidos.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "A Barbie Security bloqueou esta atualização. Uma X-API-Key válida é obrigatória."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "A profissão informada não foi encontrada no universo Barbie.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito na atualização. Já existe outra profissão utilizando esse nome.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas atualizações foram realizadas em pouco tempo. Aguarde antes de modificar outra profissão."
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProfissaoResponse> update(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id,

            @Valid @RequestBody ProfissaoRequest req
    ) {
        Profissao p = findOrThrow(id);

        if (!p.getNomeProfissao().equalsIgnoreCase(req.getNomeProfissao())
                && profissaoRepository.existsByNomeProfissaoIgnoreCase(req.getNomeProfissao())) {
            throw new ConflictException("Já existe uma profissão com o nome: " + req.getNomeProfissao());
        }

        p.setNomeProfissao(req.getNomeProfissao());
        p.setDescricao(req.getDescricao());
        p.setAreaAtuacao(req.getAreaAtuacao());

        return ResponseEntity.ok(assembler.toModel(toResponse(profissaoRepository.save(p))));
    }

    @Operation(summary = "Remover profissão")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "A profissão foi removida com sucesso do catálogo da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para remover a profissão é inválido."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida é necessária para remover profissões."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma profissão foi encontrada com esse ID.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito ao remover a profissão. Existem vínculos ativos no universo Barbie."
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "A profissão possui Barbies vinculadas. Remova os vínculos antes de excluir a carreira.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas remoções em sequência. O catálogo de carreiras bloqueou temporariamente novas exclusões."
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id
    ) {
        Profissao p = findOrThrow(id);

        if (!p.getBarbies().isEmpty()) {
            throw new BusinessException("Não é possível remover uma profissão com Barbies vinculadas.");
        }

        profissaoRepository.delete(p);

        return ResponseEntity.noContent().build();
    }

    private Profissao findOrThrow(Long id) {
        return profissaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profissao", id));
    }

    ProfissaoResponse toResponse(Profissao p) {
        return new ProfissaoResponse(
                p.getId(),
                p.getNomeProfissao(),
                p.getDescricao(),
                p.getAreaAtuacao(),
                p.getBarbies().size()
        );
    }
}