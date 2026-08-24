package com.expensesplit.repository.projection;

import java.math.BigDecimal;

/** Importe agregado por grupo, para un usuario concreto. */
public record GroupAmount(Long groupId, BigDecimal amount) {
}
