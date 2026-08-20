package com.expensesplit.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateExpenseRequest {

    @NotBlank
    private String description;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private String category;

    @NotNull
    private LocalDate expenseDate;

    // Si es null, se divide en partes iguales entre todos los miembros del grupo
    private List<Long> splitBetweenUserIds;
}
