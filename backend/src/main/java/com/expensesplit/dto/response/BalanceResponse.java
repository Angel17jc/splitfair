package com.expensesplit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Balance de un miembro del grupo, con el desglose que lo explica.
 */
@Data
@Builder
@AllArgsConstructor
public class BalanceResponse {

    private Long userId;
    private String userName;

    /** Lo que adelanto de su bolsillo. */
    private BigDecimal totalPaid;

    /** Lo que le correspondia asumir. */
    private BigDecimal totalOwed;

    /** Positivo: le deben. Negativo: debe. Cero: a paz y salvo. */
    private BigDecimal netBalance;
}
