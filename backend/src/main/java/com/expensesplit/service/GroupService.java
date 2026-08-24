package com.expensesplit.service;

import com.expensesplit.dto.request.CreateGroupRequest;
import com.expensesplit.dto.response.GroupResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.*;
import com.expensesplit.dto.response.GroupSummaryResponse;
import com.expensesplit.dto.response.PagedResponse;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.ExpenseSplitRepository;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.repository.projection.GroupAmount;
import com.expensesplit.repository.projection.GroupSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupAccessService groupAccess;

    /**
     * Grupos a los que pertenece el usuario, paginados y con su balance.
     *
     * <p>El coste es constante: una consulta para la pagina, una para el
     * total, y dos agregaciones que resuelven el balance de todos los grupos
     * de la pagina a la vez. Calcular el balance grupo a grupo convertiria el
     * listado en N+1 sobre la operacion mas cara de la aplicacion.
     */
    @Transactional(readOnly = true)
    public PagedResponse<GroupSummaryResponse> listMyGroups(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Page<GroupSummary> pagina = groupRepository.findSummariesByUserId(user.getId(), pageable);

        List<Long> idsDeLaPagina = pagina.getContent().stream()
                .map(GroupSummary::groupId)
                .toList();

        Map<Long, BigDecimal> balances = calcularBalances(user.getId(), idsDeLaPagina);

        return PagedResponse.from(pagina, resumen -> GroupSummaryResponse.builder()
                .id(resumen.groupId())
                .name(resumen.name())
                .description(resumen.description())
                .createdAt(resumen.createdAt())
                .role(resumen.role().name())
                .memberCount(resumen.memberCount())
                .myBalance(balances.getOrDefault(resumen.groupId(), CERO))
                .build());
    }

    private Map<Long, BigDecimal> calcularBalances(Long userId, List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            // Sin esta salida, el IN (:groupIds) vacio genera SQL invalido en
            // algunos dialectos y una consulta inutil en el resto.
            return Map.of();
        }

        Map<Long, BigDecimal> pagado = indexar(
                expenseRepository.sumPaidByUserPerGroup(userId, groupIds));
        Map<Long, BigDecimal> adeudado = indexar(
                expenseSplitRepository.sumOwedByUserPerGroup(userId, groupIds));

        return groupIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> pagado.getOrDefault(id, CERO)
                        .subtract(adeudado.getOrDefault(id, CERO))
                        .setScale(2, RoundingMode.HALF_UP)));
    }

    private Map<Long, BigDecimal> indexar(List<GroupAmount> totales) {
        return totales.stream()
                .collect(Collectors.toMap(GroupAmount::groupId, GroupAmount::amount));
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String creatorEmail) {
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(creator)
                .build();

        groupRepository.save(group);

        GroupMember adminMember = GroupMember.builder()
                .group(group)
                .user(creator)
                .role(GroupRole.ADMIN)
                .build();

        groupMemberRepository.save(adminMember);

        return toResponse(group, List.of(adminMember));
    }

    @Transactional
    public GroupResponse addMember(Long groupId, Long userId, String requesterEmail) {
        // Alterar la composicion del grupo es una accion de administrador.
        groupAccess.requireAdmin(groupId, requesterEmail);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("El usuario ya pertenece al grupo");
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupRole.MEMBER)
                .build();

        groupMemberRepository.save(member);

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        return toResponse(group, members);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long groupId, String requesterEmail) {
        groupAccess.requireMember(groupId, requesterEmail);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        return toResponse(group, members);
    }

    private GroupResponse toResponse(Group group, List<GroupMember> members) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .createdByName(group.getCreatedBy().getName())
                .createdAt(group.getCreatedAt())
                .members(members.stream().map(m -> GroupResponse.MemberResponse.builder()
                        .userId(m.getUser().getId())
                        .name(m.getUser().getName())
                        .email(m.getUser().getEmail())
                        .role(m.getRole().name())
                        .build()).toList())
                .build();
    }
}
