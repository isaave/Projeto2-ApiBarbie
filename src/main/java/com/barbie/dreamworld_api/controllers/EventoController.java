package com.barbie.dreamworld_api.controllers;

import com.barbie.dreamworld_api.assemblers.EventoModelAssembler;
import com.barbie.dreamworld_api.dto.request.EventoRequest;
import com.barbie.dreamworld_api.dto.response.EventoResponse;
import com.barbie.dreamworld_api.exceptions.BusinessException;
import com.barbie.dreamworld_api.exceptions.ConflictException;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.model.Evento;
import com.barbie.dreamworld_api.repository.EventoRepository;
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
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(
        name = "Eventos",
        description = "Gerenciamento dos eventos de gala do multiverso Barbie"
)
@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoRepository eventoRepository;
    private final EventoModelAssembler assembler;

    private record EventoFingerprint(String nomeEvento, String local, String dataEvento) {
    }

    private record IdempotentEventoResponse(EventoFingerprint fingerprint, EventoResponse body, URI location) {
    }

    private final ConcurrentHashMap<String, IdempotentEventoResponse> idempotencyCache = new ConcurrentHashMap<>();
    private final Object idempotencyLock = new Object();

    @Operation(summary = "Listar todos os eventos")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A agenda de eventos de gala do multiverso Barbie foi carregada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os parâmetros enviados para listar os eventos são inválidos."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas à agenda de eventos em pouco tempo. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping
    public ResponseEntity<PagedModel<EventoResponse>> listAll(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<EventoResponse> pagedAssembler
    ) {
        Page<EventoResponse> page = eventoRepository.findAll(pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Buscar evento por ID")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Evento encontrado com sucesso no calendário do multiverso Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para buscar o evento é inválido."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhum evento foi encontrado com esse ID no calendário da Dreamhouse.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas buscas por eventos em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(assembler.toModel(toResponse(findOrThrow(id))));
    }

    @Operation(summary = "Buscar eventos por nome")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Busca realizada com sucesso entre os eventos de gala da Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O nome informado para buscar o evento é inválido."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas pesquisas por eventos em sequência. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/search")
    public ResponseEntity<PagedModel<EventoResponse>> search(
            @RequestParam String nome,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<EventoResponse> pagedAssembler
    ) {
        Page<EventoResponse> page = eventoRepository.searchByNome(nome, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Listar eventos futuros")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Os próximos eventos do multiverso Barbie foram listados com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A requisição para listar eventos futuros está inválida."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas aos próximos eventos. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/futuros")
    public ResponseEntity<PagedModel<EventoResponse>> listFuturos(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<EventoResponse> pagedAssembler
    ) {
        Page<EventoResponse> page = eventoRepository.findByDataEventoAfter(LocalDate.now(), pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Listar eventos de uma Barbie")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Os eventos dessa Barbie foram encontrados com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID da Barbie informado para consultar eventos é inválido."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas aos eventos dessa Barbie. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/barbie/{barbieId}")
    public ResponseEntity<PagedModel<EventoResponse>> listByBarbie(
            @PathVariable Long barbieId,
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<EventoResponse> pagedAssembler
    ) {
        Page<EventoResponse> page = eventoRepository.findByBarbieId(barbieId, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Listar eventos por número de participantes")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Os eventos mais populares do multiverso Barbie foram listados com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "A requisição para listar eventos populares está inválida."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas consultas aos eventos mais populares. Aguarde antes de tentar novamente."
            )
    })
    @GetMapping("/mais-populares")
    public ResponseEntity<PagedModel<EventoResponse>> listMaisPopulares(
            @ParameterObject Pageable pageable,
            PagedResourcesAssembler<EventoResponse> pagedAssembler
    ) {
        Page<EventoResponse> page = eventoRepository.findMaisPopulares(pageable).map(this::toResponse);
        return ResponseEntity.ok(pagedAssembler.toModel(page, assembler));
    }

    @Operation(summary = "Cadastrar novo evento")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Novo evento de gala criado com sucesso no calendário do multiverso Barbie.",
                    headers = @Header(
                            name = "Location",
                            description = "URI do novo evento criado"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para criar o evento são inválidos. Verifique nome, descrição, data, local ou Idempotency-Key.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso negado pela Barbie Security. Uma X-API-Key válida é necessária para cadastrar eventos."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito detectado na agenda. Já existe um evento com esse nome ou a Idempotency-Key foi reutilizada incorretamente.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas tentativas de cadastro em sequência. A agenda de eventos atingiu o limite temporário de requisições."
            )
    })
    @PostMapping
    public ResponseEntity<EventoResponse> create(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @Parameter(description = "Chave única para garantir idempotência da criação", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody EventoRequest req
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("O header Idempotency-Key é obrigatório e não pode ser vazio.");
        }

        EventoFingerprint fingerprint = new EventoFingerprint(
                req.getNomeEvento(),
                req.getLocal(),
                req.getDataEvento() != null ? req.getDataEvento().toString() : null
        );

        synchronized (idempotencyLock) {
            IdempotentEventoResponse cached = idempotencyCache.get(idempotencyKey);

            if (cached != null) {
                if (!cached.fingerprint().equals(fingerprint)) {
                    throw new ConflictException("Idempotency-Key já utilizada com um payload diferente.");
                }

                return ResponseEntity.created(cached.location()).body(cached.body());
            }

            if (eventoRepository.existsByNomeEventoIgnoreCase(req.getNomeEvento())) {
                throw new ConflictException("Já existe um evento com o nome: " + req.getNomeEvento());
            }

            Evento e = new Evento();
            e.setNomeEvento(req.getNomeEvento());
            e.setDescricao(req.getDescricao());
            e.setDataEvento(req.getDataEvento());
            e.setLocal(req.getLocal());

            Evento saved = eventoRepository.save(e);

            URI location = linkTo(methodOn(EventoController.class).getById(saved.getId())).toUri();
            EventoResponse body = assembler.toModel(toResponse(saved));

            idempotencyCache.put(
                    idempotencyKey,
                    new IdempotentEventoResponse(fingerprint, body, location)
            );

            return ResponseEntity.created(location).body(body);
        }
    }

    @Operation(summary = "Atualizar evento")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "O evento foi atualizado com sucesso no calendário do multiverso Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os dados enviados para atualizar o evento são inválidos.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "A Barbie Security bloqueou esta atualização. Uma X-API-Key válida é obrigatória."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "O evento informado não foi encontrado no calendário da Dreamhouse.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito na atualização. Já existe outro evento utilizando esse nome.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas atualizações foram realizadas em pouco tempo. Aguarde antes de modificar outro evento."
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<EventoResponse> update(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id,

            @Valid @RequestBody EventoRequest req
    ) {
        Evento e = findOrThrow(id);

        if (!e.getNomeEvento().equalsIgnoreCase(req.getNomeEvento())
                && eventoRepository.existsByNomeEventoIgnoreCase(req.getNomeEvento())) {
            throw new ConflictException("Já existe um evento com o nome: " + req.getNomeEvento());
        }

        e.setNomeEvento(req.getNomeEvento());
        e.setDescricao(req.getDescricao());
        e.setDataEvento(req.getDataEvento());
        e.setLocal(req.getLocal());

        return ResponseEntity.ok(assembler.toModel(toResponse(eventoRepository.save(e))));
    }

    @Operation(summary = "Remover evento")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "O evento foi removido com sucesso da agenda do multiverso Barbie."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "O ID informado para remover o evento é inválido."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida é necessária para remover eventos."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhum evento foi encontrado com esse ID.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito ao remover o evento. Existem vínculos ativos na agenda da Dreamhouse."
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "O evento possui Barbies inscritas. Remova as participantes antes de excluir o evento.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas remoções em sequência. O sistema de eventos bloqueou temporariamente novas exclusões."
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long id
    ) {
        Evento e = findOrThrow(id);

        if (!e.getBarbies().isEmpty()) {
            throw new BusinessException(
                    "Não é possível remover o evento '" + e.getNomeEvento()
                            + "' pois ele possui " + e.getBarbies().size() + " Barbie(s) inscrita(s)."
            );
        }

        eventoRepository.delete(e);
        return ResponseEntity.noContent().build();
    }

    private Evento findOrThrow(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento", id));
    }

    EventoResponse toResponse(Evento e) {
        return new EventoResponse(
                e.getId(),
                e.getNomeEvento(),
                e.getDescricao(),
                e.getDataEvento(),
                e.getLocal(),
                e.getBarbies().size()
        );
    }
}