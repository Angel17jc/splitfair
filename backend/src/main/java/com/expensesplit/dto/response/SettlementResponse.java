package com.expensesplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Pago registrado entre dos miembros del grupo. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SettlementResponse {

    private Long id;

    private Long paidByUserId;
    private String paidByName;

    private Long paidToUserId;
    private String paidToName;

    private BigDecimal amount;
    private String currency;

    /** PENDING mientras quien cobra no lo reconoce; despues CONFIRMED. */
    private String status;

    private Instant createdAt;

    /** Solo presente cuando esta confirmada. */
    private Instant settledAt;
}
