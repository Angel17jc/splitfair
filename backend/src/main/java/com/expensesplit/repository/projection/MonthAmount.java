package com.expensesplit.repository.projection;

import java.math.BigDecimal;

/**
 * Gasto acumulado en un mes natural.
 *
 * <p>El ano y el mes viajan por separado en vez de como una cadena
 * <b>YYYY-MM</b> porque formatear fechas en JPQL depende del dialecto. Se
 * agrupa con las funciones estandar de HQL y se compone el texto en el
 * servicio, donde ordenar por (ano, mes) tambien es trivial y correcto: sobre
 * la cadena, "2026-1" iria despues de "2026-12".
 */
public record MonthAmount(int year, int month, BigDecimal total, long count) {
}
