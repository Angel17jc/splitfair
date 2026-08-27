package com.expensesplit.service.split;

import java.math.BigDecimal;

/**
 * Participante de un gasto y el valor que le corresponde segun el tipo de
 * reparto: importe exacto, porcentaje o numero de partes.
 *
 * @param userId participante
 * @param value  lo interpreta cada estrategia; irrelevante en el reparto igual
 */
public record SplitInput(Long userId, BigDecimal value) {
}
