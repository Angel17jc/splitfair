package com.expensesplit.model;

/**
 * Categoria de un gasto.
 *
 * <p>Se usa un enum y no texto libre para que los filtros y los informes
 * agreguen de verdad. Con texto libre, "comida", "Comida" y "comidas" son
 * tres categorias distintas, y cualquier grafica por categoria acaba siendo
 * una lista de errores tipograficos.
 *
 * <p>El conjunto es deliberadamente corto. Una taxonomia larga se traduce en
 * que la gente elige mal o deja de elegir, y {@link #OTROS} termina
 * absorbiendo casi todo igualmente.
 */
public enum ExpenseCategory {

    COMIDA,
    TRANSPORTE,
    ALOJAMIENTO,
    OCIO,
    SERVICIOS,
    COMPRAS,
    SALUD,
    OTROS
}
