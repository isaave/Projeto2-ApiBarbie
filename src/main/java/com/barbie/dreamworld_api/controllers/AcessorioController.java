package com.barbie.dreamworld_api.controllers;

import com.barbie.dreamworld_api.assemblers.AcessorioModelAssembler;
import com.barbie.dreamworld_api.dto.request.AcessorioRequest;
import com.barbie.dreamworld_api.dto.response.AcessorioResponse;
import com.barbie.dreamworld_api.exceptions.BusinessException;
import com.barbie.dreamworld_api.exceptions.ConflictException;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.model.Acessorio;
import com.barbie.dreamworld_api.model.Barbie;
import com.barbie.dreamworld_api.model.Raridade;
import com.barbie.dreamworld_api.repository.AcessorioRepository;
import com.barbie.dreamworld_api.repository.BarbieRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Generated;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

@Tag(
        name = "Acessórios",
        description = "Gerenciamento dos acessórios colecionáveis das Barbies"
)
@RestController
@RequestMapping("/api/acessorios")
public class AcessorioController {

    private final AcessorioRepository acessorioRepository;
    private final BarbieRepository barbieRepository;
    private final AcessorioModelAssembler assembler;

    private final ConcurrentHashMap<String, IdempotentAcessorioResponse> idempotencyCache = new ConcurrentHashMap<>();
    private final Object idempotencyLock = new Object();

    @Operation(summary = "Listar todos os acessórios")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A vitrine de acessórios da Dreamhouse foi carregada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os filtros ou parâmetros da listagem estão inválidos para consultar os acessórios."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas em pouco tempo. A vitrine de acessórios precisa de um intervalo antes de novas requisições."
            )
    })
    @GetMapping
    public ResponseEntity<PagedModel<AcessorioResponse>> listAll(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<AcessorioResponse> pagedAssembler
    ) {
        Page<AcessorioResponse> page = acessorioRepository.findAll(pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Buscar acessório por ID")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Acessório encontrado com sucesso no closet da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para buscar o acessório é inválido."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhum acessório foi encontrado com esse ID na coleção da Dreamhouse.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas buscas em sequência. Aguarde antes de procurar novos acessórios."
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<AcessorioResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(toResponse(findOrThrow(id))));
    }

    @Operation(summary = "Listar acessórios de uma Barbie")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Os acessórios dessa Barbie foram encontrados com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID da Barbie informado é inválido para consultar seus acessórios."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "A Barbie informada não foi encontrada na Dreamhouse."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas aos acessórios dessa Barbie. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/barbie/{barbieId}")
    public ResponseEntity<PagedModel<AcessorioResponse>> listByBarbie(
            @PathVariable Long barbieId,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<AcessorioResponse> pagedAssembler
    ) {
        if (!barbieRepository.existsById(barbieId)) {
            throw new ResourceNotFoundException("Barbie", barbieId);
        }

        Page<AcessorioResponse> page = acessorioRepository.findByBarbieId(barbieId, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Filtrar acessórios por raridade")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Acessórios filtrados por raridade com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A raridade informada não combina com as opções disponíveis para os acessórios."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas filtragens por raridade em pouco tempo. Aguarde antes de consultar novamente."
            )
    })
    @GetMapping("/raridade/{raridade}")
    public ResponseEntity<PagedModel<AcessorioResponse>> listByRaridade(
            @PathVariable Raridade raridade,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<AcessorioResponse> pagedAssembler
    ) {
        Page<AcessorioResponse> page = acessorioRepository.findByRaridade(raridade, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Filtrar acessórios por categoria")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Acessórios filtrados por categoria com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A categoria informada é inválida para buscar acessórios da coleção."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas por categoria em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<PagedModel<AcessorioResponse>> listByCategoria(
            @PathVariable String categoria,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<AcessorioResponse> pagedAssembler
    ) {
        Page<AcessorioResponse> page = acessorioRepository.findByCategoriaIgnoreCase(categoria, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Cadastrar novo acessório")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Acessório adicionado com sucesso à coleção da Barbie.",
                    headers = @Header(
                            name = "Location",
                            description = "URI do novo acessório criado"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados do acessório estão inválidos. Verifique nome, categoria, raridade, preço ou Idempotency-Key."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida é necessária para cadastrar acessórios na Dreamhouse."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "A Barbie escolhida para receber este acessório não foi encontrada.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito na criação do acessório. A Idempotency-Key já foi usada com dados diferentes."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas tentativas de cadastro em sequência. O closet da Barbie atingiu o limite temporário de requisições."
            )
    })
    @PostMapping
    public ResponseEntity<AcessorioResponse> create(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @Parameter(description = "Chave única para garantir idempotência da criação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @RequestBody @Valid AcessorioRequest req
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("O header Idempotency-Key é obrigatório e não pode ser vazio.");
        }

        AcessorioFingerprint fingerprint = new AcessorioFingerprint(req.getNomeItem(), req.getBarbieId());

        synchronized (idempotencyLock) {
            IdempotentAcessorioResponse cached = idempotencyCache.get(idempotencyKey);

            if (cached != null) {
                if (!cached.fingerprint().equals(fingerprint)) {
                    throw new ConflictException("Idempotency-Key já utilizada com um payload diferente.");
                }

                return ResponseEntity.created(cached.location()).body(cached.body());
            }

            Barbie barbie = barbieRepository.findById(req.getBarbieId())
                    .orElseThrow(() -> new ResourceNotFoundException("Barbie", req.getBarbieId()));

            Acessorio a = new Acessorio();
            a.setNomeItem(req.getNomeItem());
            a.setRaridade(req.getRaridade());
            a.setPreco(req.getPreco());
            a.setCategoria(req.getCategoria());
            a.setBarbie(barbie);

            Acessorio saved = acessorioRepository.save(a);

            URI location = WebMvcLinkBuilder.linkTo(
                    WebMvcLinkBuilder.methodOn(AcessorioController.class).getById(saved.getId())
            ).toUri();

            AcessorioResponse body = assembler.toModel(toResponse(saved));

            idempotencyCache.put(idempotencyKey, new IdempotentAcessorioResponse(fingerprint, body, location));

            return ResponseEntity.created(location).body(body);
        }
    }

    @Operation(summary = "Atualizar acessório")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Acessório atualizado com sucesso no closet da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para atualizar o acessório estão inválidos."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida é necessária para atualizar acessórios."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "O acessório ou a Barbie informada não foi encontrada na Dreamhouse."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito ao atualizar o acessório. Verifique se os dados enviados não entram em conflito com a coleção atual."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas atualizações em sequência. Aguarde antes de modificar outro acessório."
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<AcessorioResponse> update(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id,

            @RequestBody @Valid AcessorioRequest req
    ) {
        Acessorio a = findOrThrow(id);

        Barbie barbie = barbieRepository.findById(req.getBarbieId())
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", req.getBarbieId()));

        a.setNomeItem(req.getNomeItem());
        a.setRaridade(req.getRaridade());
        a.setPreco(req.getPreco());
        a.setCategoria(req.getCategoria());
        a.setBarbie(barbie);

        return ResponseEntity.ok(assembler.toModel(toResponse(acessorioRepository.save(a))));
    }

    @Operation(summary = "Remover acessório")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Acessório removido com sucesso da coleção da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para remover o acessório é inválido."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida é necessária para remover acessórios."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhum acessório foi encontrado com esse ID para remoção."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito ao remover o acessório. Ele pode estar vinculado a outra regra da coleção."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas remoções em sequência. Aguarde antes de retirar outro acessório do closet."
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id
    ) {
        acessorioRepository.delete(findOrThrow(id));
        return ResponseEntity.noContent().build();
    }

    private Acessorio findOrThrow(Long id) {
        return acessorioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Acessorio", id));
    }

    AcessorioResponse toResponse(Acessorio a) {
        return new AcessorioResponse(
                a.getId(),
                a.getNomeItem(),
                a.getRaridade(),
                a.getPreco(),
                a.getCategoria(),
                a.getBarbie().getNome(),
                a.getBarbie().getId()
        );
    }

    @Generated
    public AcessorioController(
            final AcessorioRepository acessorioRepository,
            final BarbieRepository barbieRepository,
            final AcessorioModelAssembler assembler
    ) {
        this.acessorioRepository = acessorioRepository;
        this.barbieRepository = barbieRepository;
        this.assembler = assembler;
    }

    private static record AcessorioFingerprint(String nomeItem, Long barbieId) {
    }

    private static record IdempotentAcessorioResponse(
            AcessorioFingerprint fingerprint,
            AcessorioResponse body,
            URI location
    ) {
    }
}