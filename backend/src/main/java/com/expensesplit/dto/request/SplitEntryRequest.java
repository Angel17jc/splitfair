package com.expensesplit.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Parte de un participante en un reparto personalizado.
 *
 * <p>El significado de {@code value} depende del tipo de reparto: importe en
 * EXACT, porcentaje en PERCENTAGE y numero de partes en SHARES. En EQUAL se
 * ignora.
 */
@Data
public class SplitEntryRequest {

    @NotNull(message = "Cada parte debe indicar el usuario")
    private Long userId;

    private BigDecimal value;
}
