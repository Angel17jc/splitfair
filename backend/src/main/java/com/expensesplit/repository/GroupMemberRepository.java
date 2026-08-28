package com.expensesplit.repository;

import com.expensesplit.model.GroupMember;
import com.expensesplit.model.GroupRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    /**
     * El grafo evita una consulta por miembro al leer su usuario, que es lo
     * que ocurria al construir la lista de miembros de un grupo.
     */
    @EntityGraph(attributePaths = "user")
    List<GroupMember> findByGroupId(Long groupId);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Busca la pertenencia a partir del email, que es lo que viaja en el JWT.
     * Evita tener que resolver antes el usuario en una consulta aparte.
     *
     * <p>Trae tambien el grupo: quien comprueba el acceso casi siempre
     * necesita despues alguno de sus datos (la moneda, el nombre), y pedirlo
     * en una consulta aparte seria una consulta de mas en cada peticion.
     */
    @EntityGraph(attributePaths = {"user", "group"})
    Optional<GroupMember> findByGroupIdAndUserEmail(Long groupId, String email);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Numero de miembros con un rol dado. Se usa para impedir que un grupo se
     * quede sin ningun administrador.
     */
    long countByGroupIdAndRole(Long groupId, GroupRole role);

    long countByGroupId(Long groupId);
}
