package com.expensesplit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    /**
     * Se exige la contrasena actual aunque el usuario ya este autenticado.
     *
     * <p>Es la misma razon que en el cambio de contrasena, con mas motivo:
     * quien se haga con un access token robado no debe poder dar de baja la
     * cuenta. Y esta operacion no tiene vuelta atras, asi que tampoco
     * conviene que la dispare un clic accidental.
     */
    @NotBlank(message = "La contrasena actual es obligatoria")
    private String currentPassword;
}
