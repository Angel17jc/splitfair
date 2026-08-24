package com.expensesplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Credenciales entregadas tras un registro, un login o un refresco.
 *
 * <p>El access token es de vida corta y se envia en cada peticion; el refresh
 * token es de vida larga, se guarda con mas cuidado y solo viaja al renovar.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    /** Segundos que le quedan de validez al access token. */
    private long expiresIn;

    private Long userId;
    private String name;
    private String email;

    /**
     * Grupo al que se ha unido el usuario si el registro traia un token de
     * invitacion. Se omite del JSON cuando es null, que es el caso habitual.
     */
    private Long joinedGroupId;
}
