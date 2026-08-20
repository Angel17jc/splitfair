package com.expensesplit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class SettlementSuggestionResponse {
    private Long fromUserId;
    private String fromUserName;
    private Long toUserId;
    private String toUserName;
    private BigDecimal amount;
}
