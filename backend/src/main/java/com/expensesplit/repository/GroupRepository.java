package com.expensesplit.repository;

import com.expensesplit.model.Group;
import com.expensesplit.repository.projection.GroupSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Grupos a los que pertenece el usuario, con su rol y el numero de
     * miembros ya contado en la base.
     *
     * <p>El recuento va como subconsulta y no como JOIN + GROUP BY porque
     * este ultimo obligaria a agrupar por todas las columnas del grupo y
     * complicaria la consulta de conteo que exige la paginacion.
     */
    @Query(value = """
            SELECT new com.expensesplit.repository.projection.GroupSummary(
                g.id, g.name, g.description, g.createdAt, m.role,
                (SELECT COUNT(m2) FROM GroupMember m2 WHERE m2.group.id = g.id))
            FROM GroupMember m
            JOIN m.group g
            WHERE m.user.id = :userId
            ORDER BY g.createdAt DESC, g.id DESC
            """,
            countQuery = "SELECT COUNT(m) FROM GroupMember m WHERE m.user.id = :userId")
    Page<GroupSummary> findSummariesByUserId(@Param("userId") Long userId, Pageable pageable);
}
