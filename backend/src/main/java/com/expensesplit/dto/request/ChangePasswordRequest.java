package com.expensesplit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    /**
     * Se exige la contrasena actual aunque el usuario ya este autenticado:
     * si alguien se hace con un access token, no debe poder apropiarse de la
     * cuenta cambiando la contrasena.
     */
    @NotBlank(message = "La contrasena actual es obligatoria")
    private String currentPassword;

    @NotBlank(message = "La contrasena nueva es obligatoria")
    @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
    private String newPassword;
}
