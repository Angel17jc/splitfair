package com.expensesplit.repository;

import com.expensesplit.model.Invitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /**
     * El grafo trae el grupo y quien invita en la misma consulta: la vista
     * previa de la invitacion necesita ambos.
     */
    @EntityGraph(attributePaths = {"group", "invitedBy"})
    Optional<Invitation> findByTokenHash(String tokenHash);

    /**
     * Elimina las invitaciones caducadas. Pensado para una tarea programada:
     * sin ella la tabla crece indefinidamente.
     */
    @Modifying
    @Query("DELETE FROM Invitation i WHERE i.expiresAt < :limite")
    int deleteExpiredBefore(@Param("limite") Instant limite);
}
