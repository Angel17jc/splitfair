package com.expensesplit.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.expensesplit.model.SplitType;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reemplaza por completo los datos de un gasto. No es un PATCH: los campos
 * omitidos no se conservan, se recalculan. Un gasto es una unidad contable, y
 * modificar su importe sin revisar entre quienes se reparte dejaria las
 * cuentas incoherentes.
 */
@Data
public class UpdateExpenseRequest {

    @NotBlank(message = "La descripcion es obligatoria")
    @Size(max = 200, message = "La descripcion no puede superar los 200 caracteres")
    private String description;

    @NotNull(message = "El importe es obligatorio")
    @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero")
    private BigDecimal amount;

    @Size(max = 60, message = "La categoria no puede superar los 60 caracteres")
    private String category;

    @NotNull(message = "La fecha del gasto es obligatoria")
    private LocalDate expenseDate;

    /** Si es null, se reparte entre todos los miembros del grupo. */
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
