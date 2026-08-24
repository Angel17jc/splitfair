package com.expensesplit.repository.projection;

import com.expensesplit.model.GroupRole;

import java.time.LocalDateTime;

/**
 * Fila del listado de grupos de un usuario, tal como la devuelve la base.
 *
 * <p>El balance no viaja aqui: se calcula aparte con una agregacion por
 * grupo, para no convertir el listado en una consulta por fila.
 */
public record GroupSummary(
        Long groupId,
        String name,
        String description,
        LocalDateTime createdAt,
        GroupRole role,
        long memberCount) {
}
