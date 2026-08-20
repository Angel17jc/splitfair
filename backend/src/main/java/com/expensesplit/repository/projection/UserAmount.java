package com.expensesplit.repository.projection;

import java.math.BigDecimal;

/**
 * Importe agregado por usuario, devuelto por las consultas de agrupacion.
 * Permite sumar en la base de datos en vez de recorrer entidades en memoria.
 */
public record UserAmount(Long userId, BigDecimal amount) {
}
