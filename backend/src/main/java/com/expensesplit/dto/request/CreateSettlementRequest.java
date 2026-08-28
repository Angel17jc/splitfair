package com.expensesplit.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Registro de un pago real entre dos miembros del grupo.
 *
 * <p>Quien paga es siempre el usuario autenticado: no se indica en el cuerpo.
 * Permitir declarar pagos ajenos abriria la puerta a que cualquiera diera por
 * saldada la deuda de otro sin su conocimiento.
 */
@Data
public class CreateSettlementRequest {

    @NotNull(message = "Hay que indicar a quien se le paga")
    private Long paidTo;

    @NotNull(message = "El importe es obligatorio")
    @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero")
    private BigDecimal amount;
}
