package com.expensesplit.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Token de refresco emitido a un usuario.
 *
 * <p>Solo se almacena el hash: la entidad nunca conoce el token en claro, de
 * modo que un volcado de esta tabla no permite suplantar ninguna sesion.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    /**
     * Identifica la cadena de tokens nacida de un mismo inicio de sesion.
     * Al rotar, el token nuevo hereda esta familia.
     */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null mientras el token sigue siendo utilizable. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    /** Un token sirve si no ha sido revocado ni ha caducado. */
    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
