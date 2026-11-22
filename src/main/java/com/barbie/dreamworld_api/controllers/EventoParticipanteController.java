package com.barbie.dreamworld_api.controllers;

import com.barbie.dreamworld_api.exceptions.BusinessException;
import com.barbie.dreamworld_api.exceptions.ResourceNotFoundException;
import com.barbie.dreamworld_api.model.Barbie;
import com.barbie.dreamworld_api.model.Evento;
import com.barbie.dreamworld_api.repository.BarbieRepository;
import com.barbie.dreamworld_api.repository.EventoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Eventos",
        description = "Gerenciamento das participantes dos eventos de gala do multiverso Barbie"
)
@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventoParticipanteController {

    private final EventoRepository eventoRepository;
    private final BarbieRepository barbieRepository;

    @Operation(summary = "Inscrever Barbie em um Evento")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "A Barbie foi inscrita com sucesso no evento de gala."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os IDs informados para inscrever a Barbie no evento são inválidos."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso negado pela Barbie Security. Uma X-API-Key válida é necessária para inscrever participantes."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "O evento ou a Barbie informada não foi encontrada no multiverso Barbie.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito na inscrição. A Barbie já possui vínculo com este evento."
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "A Barbie já está inscrita neste evento de gala.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas inscrições em sequência. A lista de convidados da Dreamhouse atingiu o limite temporário de requisições."
            )
    })
    @PutMapping("/{eventoId}/participantes/{barbieId}")
    public ResponseEntity<Void> inscrever(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long eventoId,
            @PathVariable Long barbieId
    ) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento", eventoId));

        Barbie barbie = barbieRepository.findById(barbieId)
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", barbieId));

        if (barbie.getEventos().contains(evento)) {
            throw new BusinessException("Barbie já está inscrita neste evento.");
        }

        barbie.getEventos().add(evento);
        barbieRepository.save(barbie);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remover Barbie de um Evento")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "A Barbie foi removida com sucesso da lista de convidadas do evento."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Os IDs informados para remover a Barbie do evento são inválidos."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Acesso não autorizado. Uma X-API-Key válida é necessária para remover participantes do evento."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "O evento ou a Barbie informada não foi encontrada no multiverso Barbie.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito na remoção. A Barbie não possui vínculo ativo com este evento."
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Muitas remoções em sequência. A lista de convidados da Dreamhouse bloqueou temporariamente novas alterações."
            )
    })
    @DeleteMapping("/{eventoId}/participantes/{barbieId}")
    public ResponseEntity<Void> remover(
            @Parameter(description = "Chave de API para autenticação", required = true)
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,

            @PathVariable Long eventoId,
            @PathVariable Long barbieId
    ) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento", eventoId));

        Barbie barbie = barbieRepository.findById(barbieId)
                .orElseThrow(() -> new ResourceNotFoundException("Barbie", barbieId));

        barbie.getEventos().remove(evento);
        barbieRepository.save(barbie);

        return ResponseEntity.noContent().build();
    }
}