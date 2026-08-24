package com.expensesplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Invitacion recien creada.
 *
 * <p>El token en claro viaja aqui una unica vez: en la base solo queda su
 * hash, de modo que no hay forma de recuperarlo despues. Si se pierde, se
 * genera otra invitacion.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvitationResponse {

    private Long id;

    /** Link listo para compartir. */
    private String url;

    private String token;

    /** Destinatario previsto, si se fijo uno. */
    private String email;

    private Instant expiresAt;
}
