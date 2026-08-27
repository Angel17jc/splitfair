package com.expensesplit.dto.request;

import jakarta.validation.constraints.NotBlank;
import com.expensesplit.validation.ValidCurrency;
import lombok.Data;

@Data
public class CreateGroupRequest {

    @NotBlank
    private String name;

    private String description;

    /**
     * Moneda del grupo (ISO 4217). Opcional: si se omite se usa la moneda por
     * defecto de la aplicacion. No se puede cambiar despues.
     */
    @ValidCurrency
    private String currency;
}
