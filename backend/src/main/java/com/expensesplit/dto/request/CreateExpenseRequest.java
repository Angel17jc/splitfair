package com.expensesplit.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.expensesplit.model.ExpenseCategory;
import com.expensesplit.model.SplitType;
import jakarta.validation.Valid;
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

    /** Si se omite, el gasto queda como OTROS. */
    private ExpenseCategory category;

    @NotNull
    private LocalDate expenseDate;

    // Si es null, se divide en partes iguales entre todos los miembros del grupo
    private List<Long> splitBetweenUserIds;

    /**
     * Como repartir el gasto. Si se omite se reparte a partes iguales, que
     * era el unico modo disponible antes de existir este campo.
     */
    private SplitType splitType;

    /**
     * Participantes y su valor. Obligatorio salvo en el reparto igual, que
     * admite ademas la forma abreviada splitBetweenUserIds.
     */
    @Valid
    private List<SplitEntryRequest> splits;
}
