package com.expensesplit.dto.response;

import com.expensesplit.model.ExpenseCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reparto del gasto de un grupo por categoria y por mes.
 *
 * <h2>Por que existe este endpoint</h2>
 * El cliente podria pedir todos los gastos y sumarlos. No debe, por dos
 * razones distintas y las dos suficientes:
 *
 * <ol>
 *   <li>El listado esta paginado. Sumando lo que hay cargado saldrian totales
 *       parciales presentados como completos, que es peor que no ensenar
 *       ningun grafico.</li>
 *   <li>Sumar dinero en JavaScript es sumar en coma flotante. Aqui se agrega
 *       en SQL sobre NUMERIC, igual que los balances, y el resultado no
 *       depende de en que orden se sumaron las filas.</li>
 * </ol>
 */
@Data
@Builder
public class GroupAnalyticsResponse {

    /** Moneda en la que estan expresados todos los importes. */
    private String currency;

    /** Suma de todos los gastos del grupo. No incluye liquidaciones. */
    private BigDecimal totalSpent;

    /** Solo las categorias con gasto, de mayor a menor. */
    private List<CategoryTotal> byCategory;

    /** Meses con gasto, del mas antiguo al mas reciente. */
    private List<MonthTotal> byMonth;

    @Data
    @Builder
    public static class CategoryTotal {
        private ExpenseCategory category;
        private BigDecimal total;
        /** Cuantos gastos suman ese total. */
        private long count;
    }

    @Data
    @Builder
    public static class MonthTotal {
        /** Mes natural en formato <b>YYYY-MM</b>. */
        private String month;
        private BigDecimal total;
        private long count;
    }
}
