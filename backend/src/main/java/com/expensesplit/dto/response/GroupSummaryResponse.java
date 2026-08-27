package com.expensesplit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Grupo tal como aparece en el listado del usuario.
 *
 * <p>No incluye la lista de miembros ni los gastos: para eso esta
 * {@code GET /api/groups/{id}}. Un listado que arrastrase todo el detalle
 * crece sin control con el numero de grupos.
 */
@Data
@Builder
public class GroupSummaryResponse {

    private Long id;
    private String name;
    private String description;
    private String currency;
    private LocalDateTime createdAt;

    /** Rol del usuario que consulta dentro de este grupo. */
    private String role;

    private long memberCount;

    /**
     * Balance del usuario que consulta en este grupo.
     * Positivo: le deben. Negativo: debe. Cero: a paz y salvo.
     */
    private BigDecimal myBalance;
}
