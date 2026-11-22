package com.barbie.dreamworld_api.controllers;

import com.barbie.dreamworld_api.assemblers.DreamhouseModelAssembler;
import com.barbie.dreamworld_api.dto.request.DreamhouseRequest;
import com.barbie.dreamworld_api.dto.response.DreamhouseResponse;
import com.barbie.dreamworld_api.exceptions.BusinessException;
import com.barbie.dreamworld_api.exceptions.ConflictException;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.model.Dreamhouse;
import com.barbie.dreamworld_api.repository.DreamhouseRepository;
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
        name = "Dreamhouses",
        description = "Gerenciamento das residências das Barbies"
)
@RestController
@RequestMapping("/api/dreamhouses")
@RequiredArgsConstructor
public class DreamhouseController {

    private final DreamhouseRepository dreamhouseRepository;
    private final DreamhouseModelAssembler assembler;

    private record DreamhouseFingerprint(String nomeCasa, String endereco, String cor, Integer numeroQuartos) {
    }

    private record IdempotentDreamhouseResponse(DreamhouseFingerprint fingerprint, DreamhouseResponse body, URI location) {
    }

    private final ConcurrentHashMap<String, IdempotentDreamhouseResponse> idempotencyCache = new ConcurrentHashMap<>();
    private final Object idempotencyLock = new Object();

    @Operation(summary = "Listar todas as Dreamhouses")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "As Dreamhouses disponíveis no universo Barbie foram carregadas com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os parâmetros enviados para listar as Dreamhouses são inválidos."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas visitas ao catálogo de Dreamhouses em pouco tempo. Aguarde antes de consultar novamente."
            )
    })
    @GetMapping
    public ResponseEntity<PagedModel<DreamhouseResponse>> listAll(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<DreamhouseResponse> pagedAssembler
    ) {
        Page<DreamhouseResponse> page = dreamhouseRepository.findAll(pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Buscar Dreamhouse por ID")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Dreamhouse encontrada com sucesso no mapa da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para buscar a Dreamhouse é inválido."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma Dreamhouse foi encontrada com esse ID no universo Barbie.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas buscas por Dreamhouses em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<DreamhouseResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(toResponse(findOrThrow(id))));
    }

    @Operation(summary = "Buscar Dreamhouse por nome")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Busca realizada com sucesso entre as Dreamhouses da coleção."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O nome informado para buscar a Dreamhouse é inválido."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas pesquisas por Dreamhouses em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/search")
    public ResponseEntity<PagedModel<DreamhouseResponse>> search(
            @RequestParam String nome,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<DreamhouseResponse> pagedAssembler
    ) {
        Page<DreamhouseResponse> page = dreamhouseRepository.searchByNome(nome, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Listar Dreamhouses sem proprietária")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Dreamhouses livres para receber uma nova Barbie foram listadas com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A requisição para listar Dreamhouses sem proprietária está inválida."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas às Dreamhouses disponíveis. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/sem-proprietaria")
    public ResponseEntity<PagedModel<DreamhouseResponse>> listSemProprietaria(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<DreamhouseResponse> pagedAssembler
    ) {
        Page<DreamhouseResponse> page = dreamhouseRepository.findSemProprietaria(pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Buscar Dreamhouse da Barbie")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A Dreamhouse da Barbie foi encontrada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID da Barbie informado é inválido."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Essa Barbie ainda não possui uma Dreamhouse cadastrada.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas à Dreamhouse dessa Barbie. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/barbie/{barbieId}")
    public ResponseEntity<DreamhouseResponse> getByBarbie(@PathVariable Long barbieId) {
        Dreamhouse d = dreamhouseRepository.findByBarbieId(barbieId)
                .orElseThrow(() -> new ResourceNotFoundException("Dreamhouse da Barbie " + barbieId + " não encontrada"));

        return ResponseEntity.ok(assembler.toModel(toResponse(d)));
    }

    @Operation(summary = "Cadastrar nova Dreamhouse")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Nova Dreamhouse criada com sucesso para o universo Barbie.",
                    headers = @Header(
                            name = "Location",
                            description = "URI da nova Dreamhouse criada"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para criar a Dreamhouse são inválidos. Verifique nome, endereço, cor, número de quartos ou Idempotency-Key.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso negado pela Barbie Security. Uma X-API-Key válida é necessária para cadastrar Dreamhouses."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito detectado no catálogo. Já existe uma Dreamhouse com esse nome ou a Idempotency-Key foi reutilizada incorretamente.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas tentativas de cadastro em sequência. O sistema da Dreamhouse atingiu o limite temporário de requisições."
            )
    })
    @PostMapping
    public ResponseEntity<DreamhouseResponse> create(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @Parameter(description = "Chave única para garantir idempotência da criação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody DreamhouseRequest req
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("O header Idempotency-Key é obrigatório e não pode ser vazio.");
        }

        DreamhouseFingerprint fingerprint = new DreamhouseFingerprint(
                req.getNomeCasa(),
                req.getEndereco(),
                req.getCor(),
                req.getNumeroQuartos()
        );

        synchronized (idempotencyLock) {
            IdempotentDreamhouseResponse cached = idempotencyCache.get(idempotencyKey);

            if (cached != null) {
                if (!cached.fingerprint().equals(fingerprint)) {
                    throw new ConflictException("Idempotency-Key já utilizada com um payload diferente.");
                }

                return ResponseEntity.created(cached.location()).body(cached.body());
            }

            if (dreamhouseRepository.existsByNomeCasaIgnoreCase(req.getNomeCasa())) {
                throw new ConflictException("Já existe uma Dreamhouse com o nome: " + req.getNomeCasa());
            }

            Dreamhouse d = new Dreamhouse();
            d.setNomeCasa(req.getNomeCasa());
            d.setEndereco(req.getEndereco());
            d.setCor(req.getCor());
            d.setNumeroQuartos(req.getNumeroQuartos());

            Dreamhouse saved = dreamhouseRepository.save(d);

            URI location = linkTo(methodOn(DreamhouseController.class).getById(saved.getId())).toUri();
            DreamhouseResponse body = assembler.toModel(toResponse(saved));

            idempotencyCache.put(
                    idempotencyKey,
                    new IdempotentDreamhouseResponse(fingerprint, body, location)
            );

            return ResponseEntity.created(location).body(body);
        }
    }

    @Operation(summary = "Atualizar Dreamhouse")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A Dreamhouse foi atualizada com sucesso no universo Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para atualizar a Dreamhouse são inválidos.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "A Barbie Security bloqueou esta atualização. Uma X-API-Key válida é obrigatória."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "A Dreamhouse informada não foi encontrada.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito na atualização. Já existe outra Dreamhouse utilizando esse nome.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas atualizações foram realizadas em pouco tempo. Aguarde antes de modificar outra Dreamhouse."
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<DreamhouseResponse> update(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id,

            @Valid @RequestBody DreamhouseRequest req
    ) {
        Dreamhouse d = findOrThrow(id);

        if (!d.getNomeCasa().equalsIgnoreCase(req.getNomeCasa())
                && dreamhouseRepository.existsByNomeCasaIgnoreCase(req.getNomeCasa())) {
            throw new ConflictException("Já existe uma Dreamhouse com o nome: " + req.getNomeCasa());
        }

        d.setNomeCasa(req.getNomeCasa());
        d.setEndereco(req.getEndereco());
        d.setCor(req.getCor());
        d.setNumeroQuartos(req.getNumeroQuartos());

        return ResponseEntity.ok(assembler.toModel(toResponse(dreamhouseRepository.save(d))));
    }

    @Operation(summary = "Remover Dreamhouse")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "A Dreamhouse foi removida com sucesso do catálogo Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para remover a Dreamhouse é inválido."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida é necessária para remover Dreamhouses."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma Dreamhouse foi encontrada com esse ID.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito ao remover a Dreamhouse. Existem vínculos ativos no sistema da Dreamhouse."
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "A Dreamhouse possui uma Barbie proprietária. Desvincule a Barbie antes de remover a casa.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas remoções em sequência. O sistema da Dreamhouse bloqueou temporariamente novas exclusões."
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id
    ) {
        Dreamhouse d = findOrThrow(id);

        if (d.getBarbie() != null) {
            throw new BusinessException(
                    "Não é possível remover a Dreamhouse '" + d.getNomeCasa()
                            + "' pois ela pertence à Barbie '" + d.getBarbie().getNome() + "'."
            );
        }

        dreamhouseRepository.delete(d);
        return ResponseEntity.noContent().build();
    }

    private Dreamhouse findOrThrow(Long id) {
        return dreamhouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dreamhouse", id));
    }

    DreamhouseResponse toResponse(Dreamhouse d) {
        return new DreamhouseResponse(
                d.getId(),
                d.getNomeCasa(),
                d.getEndereco(),
                d.getCor(),
                d.getNumeroQuartos(),
                d.getBarbie() != null ? d.getBarbie().getNome() : null
        );
    }
}