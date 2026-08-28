package com.expensesplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Credenciales entregadas tras un registro, un login o un refresco.
 *
 * <p>Aqui solo viaja el access token. El refresh token <b>no aparece en el
 * cuerpo</b> a proposito: sale en una cookie HttpOnly (ver RefreshCookie), de
 * modo que el navegador lo envia solo a /api/auth y ningun script puede
 * leerlo. Devolverlo tambien aqui anularia esa proteccion, porque bastaria un
 * XSS leyendo la respuesta del login para llevarselo.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;

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
