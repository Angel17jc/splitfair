package com.expensesplit.repository;

import com.expensesplit.model.Settlement;
import com.expensesplit.model.SettlementStatus;
import com.expensesplit.repository.projection.GroupAmount;
import com.expensesplit.repository.projection.UserAmount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** El grafo evita una consulta por fila al mostrar los nombres. */
    @EntityGraph(attributePaths = {"paidBy", "paidTo"})
    Page<Settlement> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);

    @EntityGraph(attributePaths = {"group", "paidBy", "paidTo"})
    Optional<Settlement> findWithDetailsById(Long id);

    /**
     * Total que cada usuario ha entregado en liquidaciones confirmadas del
     * grupo. Solo las confirmadas alteran las cuentas: una pendiente es la
     * palabra de una sola parte.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.UserAmount(s.paidBy.id, SUM(s.amount))
            FROM Settlement s
            WHERE s.group.id = :groupId AND s.status = :status
            GROUP BY s.paidBy.id
            """)
    List<UserAmount> sumPaidOutByUser(@Param("groupId") Long groupId,
                                      @Param("status") SettlementStatus status);

    /** Total que cada usuario ha recibido en liquidaciones confirmadas. */
    @Query("""
            SELECT new com.expensesplit.repository.projection.UserAmount(s.paidTo.id, SUM(s.amount))
            FROM Settlement s
            WHERE s.group.id = :groupId AND s.status = :status
            GROUP BY s.paidTo.id
            """)
    List<UserAmount> sumReceivedByUser(@Param("groupId") Long groupId,
                                       @Param("status") SettlementStatus status);

    /**
     * Lo entregado y lo cobrado por un usuario en cada uno de sus grupos, en
     * una sola consulta. Sin esto, el listado de grupos mostraria un balance
     * distinto al del detalle en cuanto alguien saldara una deuda.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.GroupAmount(s.group.id, SUM(s.amount))
            FROM Settlement s
            WHERE s.paidBy.id = :userId AND s.status = :status AND s.group.id IN :groupIds
            GROUP BY s.group.id
            """)
    List<GroupAmount> sumPaidOutByUserPerGroup(@Param("userId") Long userId,
                                               @Param("status") SettlementStatus status,
                                               @Param("groupIds") Collection<Long> groupIds);

    @Query("""
            SELECT new com.expensesplit.repository.projection.GroupAmount(s.group.id, SUM(s.amount))
            FROM Settlement s
            WHERE s.paidTo.id = :userId AND s.status = :status AND s.group.id IN :groupIds
            GROUP BY s.group.id
            """)
    List<GroupAmount> sumReceivedByUserPerGroup(@Param("userId") Long userId,
                                                @Param("status") SettlementStatus status,
                                                @Param("groupIds") Collection<Long> groupIds);
}
