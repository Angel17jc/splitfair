package com.expensesplit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Balances del grupo con el contexto necesario para presentarlos.
 *
 * <p>Antes el endpoint devolvia un array pelado de importes. Eso obligaba al
 * cliente a pedir el grupo aparte solo para saber en que moneda estaban esas
 * cifras, y no habia forma de mostrar el gasto total sin recorrer todas las
 * paginas del listado de gastos.
 */
@Data
@Builder
public class GroupBalanceResponse {

    /** Moneda en la que estan expresados todos los importes. */
    private String currency;

    /** Suma de todos los gastos registrados en el grupo. */
    private BigDecimal totalSpent;

    /** Un balance por miembro, acreedores primero. */
    private List<BalanceResponse> balances;
}
