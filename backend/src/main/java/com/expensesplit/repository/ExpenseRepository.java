package com.expensesplit.repository;

import com.expensesplit.model.Expense;
import com.expensesplit.model.ExpenseCategory;
import com.expensesplit.repository.projection.GroupAmount;
import com.expensesplit.repository.projection.UserAmount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * Trae los gastos con quien pago, sus splits y los usuarios de cada split
     * en una sola consulta. Sin el grafo, recorrer las colecciones perezosas
     * al construir la respuesta dispara una consulta por gasto y otra por
     * split (problema N+1).
     */
    @EntityGraph(attributePaths = {"paidBy", "splits", "splits.user"})
    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);

    /** Trae el gasto con todo lo que hace falta para editarlo o describirlo. */
    @EntityGraph(attributePaths = {"group", "paidBy", "splits", "splits.user"})
    Optional<Expense> findWithDetailsById(Long id);

    /**
     * Pagina los gastos de un grupo aplicando los filtros que vengan
     * informados. Un filtro nulo no restringe.
     *
     * <p><b>Devuelve identificadores y no entidades a proposito.</b> Paginar
     * una consulta que ademas trae una coleccion (los splits) obliga a
     * Hibernate a cargar todas las filas y paginar en memoria: emite un aviso
     * y, con suficientes gastos, se lleva el servidor por delante. Separando
     * la busqueda de la carga, la paginacion la resuelve la base de datos con
     * LIMIT y OFFSET, y despues se hidratan solo los gastos de la pagina.
     */
    @Query(value = """
            SELECT e.id FROM Expense e
            WHERE e.group.id = :groupId
              AND (:category IS NULL OR e.category = :category)
              AND (CAST(:desde AS date) IS NULL OR e.expenseDate >= :desde)
              AND (CAST(:hasta AS date) IS NULL OR e.expenseDate <= :hasta)
              AND (:paidBy IS NULL OR e.paidBy.id = :paidBy)
            ORDER BY e.expenseDate DESC, e.id DESC
            """,
            countQuery = """
                    SELECT COUNT(e) FROM Expense e
                    WHERE e.group.id = :groupId
                      AND (:category IS NULL OR e.category = :category)
                      AND (CAST(:desde AS date) IS NULL OR e.expenseDate >= :desde)
                      AND (CAST(:hasta AS date) IS NULL OR e.expenseDate <= :hasta)
                      AND (:paidBy IS NULL OR e.paidBy.id = :paidBy)
                    """)
    Page<Long> findIdsByFilters(@Param("groupId") Long groupId,
                                @Param("category") ExpenseCategory category,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta,
                                @Param("paidBy") Long paidBy,
                                Pageable pageable);

    /**
     * Hidrata los gastos de una pagina con todas sus relaciones, en una sola
     * consulta. El orden lo impone quien llama a partir de la pagina de
     * identificadores: un IN no garantiza ninguno.
     */
    @EntityGraph(attributePaths = {"paidBy", "splits", "splits.user"})
    List<Expense> findByIdIn(Collection<Long> ids);

    /**
     * Total desembolsado por cada usuario en el grupo, sumado en la base de
     * datos. Devuelve solo a quienes pagaron algo.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.UserAmount(e.paidBy.id, SUM(e.amount))
            FROM Expense e
            WHERE e.group.id = :groupId
            GROUP BY e.paidBy.id
            """)
    List<UserAmount> sumPaidByUser(@Param("groupId") Long groupId);

    /**
     * Total desembolsado por un usuario en cada uno de sus grupos, en una
     * sola consulta. Es lo que evita que el listado de grupos dispare una
     * agregacion por fila.
     */
    @Query("""
            SELECT new com.expensesplit.repository.projection.GroupAmount(e.group.id, SUM(e.amount))
            FROM Expense e
            WHERE e.paidBy.id = :userId AND e.group.id IN :groupIds
            GROUP BY e.group.id
            """)
    List<GroupAmount> sumPaidByUserPerGroup(@Param("userId") Long userId,
                                            @Param("groupIds") Collection<Long> groupIds);
}
