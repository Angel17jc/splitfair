package com.expensesplit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    private String email;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
    private String password;

    /**
     * Token de invitacion, opcional. Permite crear la cuenta y unirse al
     * grupo en un solo paso.
     *
     * <p>Hacerlo en una sola peticion no es comodidad: es atomicidad. Con dos
     * llamadas separadas, un fallo entre ambas deja al usuario registrado
     * pero fuera del grupo al que le habian invitado, y con la invitacion aun
     * sin consumir o ya gastada segun el orden.
     */
    private String invitationToken;
}
