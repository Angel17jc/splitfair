package com.expensesplit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private String description;
    private BigDecimal amount;
    private String category;
    private String splitType;
    private LocalDate expenseDate;
    private String paidByName;
    private List<SplitResponse> splits;

    @Data
    @Builder
    @AllArgsConstructor
    public static class SplitResponse {
        private Long userId;
        private String userName;
        private BigDecimal amountOwed;
    }
}
