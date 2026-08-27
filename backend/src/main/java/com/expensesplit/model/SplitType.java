package com.expensesplit.model;

/**
 * Forma en que se reparte un gasto entre sus participantes.
 */
public enum SplitType {

    /** Partes iguales. El valor de cada entrada se ignora. */
    EQUAL,

    /** Importe exacto por participante. Deben sumar el total del gasto. */
    EXACT,

    /** Porcentaje por participante. Deben sumar 100. */
    PERCENTAGE,

    /** Partes proporcionales: 2 partes pagan el doble que 1. */
    SHARES
}
