package com.expensesplit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInvitationRequest {

    /**
     * Destinatario previsto, opcional. Si se indica, solo esa direccion podra
     * aceptar la invitacion: reenviar el link a un tercero no servira de
     * nada. Si se omite, vale para quien reciba el link.
     */
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 180, message = "El email no puede superar los 180 caracteres")
    private String email;
}
