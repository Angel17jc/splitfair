package com.expensesplit.repository.projection;

import com.expensesplit.model.ExpenseCategory;

import java.math.BigDecimal;

/**
 * Gasto acumulado en una categoria, agregado por la base de datos.
 *
 * <p>Se suma en SQL y no recorriendo entidades en memoria por la misma razon
 * que los balances: el coste no depende del numero de gastos, y sobre todo la
 * suma la hace PostgreSQL sobre NUMERIC, no el cliente sobre coma flotante.
 */
public record CategoryAmount(ExpenseCategory category, BigDecimal total, long count) {
}
