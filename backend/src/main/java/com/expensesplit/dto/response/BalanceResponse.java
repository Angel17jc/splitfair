package com.expensesplit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class BalanceResponse {
    private Long userId;
    private String userName;
    // positivo = le deben; negativo = debe
    private BigDecimal netBalance;
}
