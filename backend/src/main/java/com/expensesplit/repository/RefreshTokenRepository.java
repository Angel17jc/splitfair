package com.expensesplit.repository;

import com.expensesplit.model.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** El grafo evita una consulta extra para el usuario en cada refresco. */
    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoca de golpe todos los tokens vivos de una familia. Se invoca cuando
     * se detecta la reutilizacion de un token ya rotado, senal de que alguien
     * tiene una copia.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t SET t.revokedAt = :now
            WHERE t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /** Revoca todas las sesiones abiertas de un usuario. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t SET t.revokedAt = :now
            WHERE t.user.id = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Elimina los tokens caducados. Se conservan los revocados no caducados
     * porque son los que permiten detectar una reutilizacion.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :limite")
    int deleteExpiredBefore(@Param("limite") Instant limite);
}
