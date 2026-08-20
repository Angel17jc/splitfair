package com.expensesplit.repository;

import com.expensesplit.model.GroupMember;
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
     */
    Optional<GroupMember> findByGroupIdAndUserEmail(Long groupId, String email);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
}
