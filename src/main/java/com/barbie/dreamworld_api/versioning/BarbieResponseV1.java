package com.barbie.dreamworld_api.versioning;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resposta simplificada — API v1.
 * Retorna apenas os campos essenciais da Barbie.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Resposta simplificada (v1): apenas id, nome e coleção")
public class BarbieResponseV1 {

    @Schema(description = "ID da Barbie", example = "1")
    private Long id;

    @Schema(description = "Nome da Barbie", example = "Barbie Cientista")
    private String nome;

    @Schema(description = "Coleção da Barbie", example = "Barbie Careers")
    private String colecao;
}
