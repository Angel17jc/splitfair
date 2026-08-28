package com.expensesplit.service;

import com.expensesplit.dto.request.CreateGroupRequest;
import com.expensesplit.dto.request.UpdateGroupRequest;
import com.expensesplit.dto.response.GroupResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ForbiddenException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.*;
import com.expensesplit.model.SettlementStatus;
import com.expensesplit.dto.response.GroupSummaryResponse;
import com.expensesplit.dto.response.PagedResponse;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.ExpenseSplitRepository;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.SettlementRepository;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.repository.projection.GroupAmount;
import com.expensesplit.repository.projection.GroupSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    @Value("${app.default-currency}")
    private String defaultCurrency;

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final SettlementRepository settlementRepository;
    private final GroupAccessService groupAccess;
    private final BalanceService balanceService;

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
                .currency(resumen.currency())
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

        // Las liquidaciones confirmadas entran en el calculo igual que en el
        // detalle. Si no, el listado mostraria una deuda que el usuario acaba
        // de saldar y que ya no aparece al entrar en el grupo.
        Map<Long, BigDecimal> entregado = indexar(settlementRepository
                .sumPaidOutByUserPerGroup(userId, SettlementStatus.CONFIRMED, groupIds));
        Map<Long, BigDecimal> cobrado = indexar(settlementRepository
                .sumReceivedByUserPerGroup(userId, SettlementStatus.CONFIRMED, groupIds));

        return groupIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> pagado.getOrDefault(id, CERO)
                        .subtract(adeudado.getOrDefault(id, CERO))
                        .add(entregado.getOrDefault(id, CERO))
                        .subtract(cobrado.getOrDefault(id, CERO))
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
                .currency(resolverMoneda(request.getCurrency()))
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

    /**
     * Edita el nombre y la descripcion. Es una accion de administrador: son
     * datos compartidos por todo el grupo.
     */
    @Transactional
    public GroupResponse updateGroup(Long groupId, UpdateGroupRequest request, String requesterEmail) {
        groupAccess.requireAdmin(groupId, requesterEmail);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        group.setName(request.getName().trim());
        group.setDescription(request.getDescription() == null
                ? null : request.getDescription().trim());
        groupRepository.save(group);

        return toResponse(group, groupMemberRepository.findByGroupId(groupId));
    }

    /**
     * Cambia el rol de un miembro.
     *
     * <p><b>Invariante:</b> un grupo nunca puede quedarse sin administrador.
     * Si se permitiera, nadie podria volver a invitar, expulsar ni editar el
     * grupo: quedaria congelado para siempre y sin via de recuperacion desde
     * la propia aplicacion.
     */
    @Transactional
    public GroupResponse changeMemberRole(Long groupId, Long userId,
                                            GroupRole nuevoRol, String requesterEmail) {
        groupAccess.requireAdmin(groupId, requesterEmail);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no pertenece al grupo"));

        if (member.getRole() == nuevoRol) {
            // Idempotente: pedir el rol que ya se tiene no es un error.
            return toResponse(findGroup(groupId), groupMemberRepository.findByGroupId(groupId));
        }

        if (nuevoRol == GroupRole.MEMBER && esElUnicoAdmin(groupId)) {
            throw new BadRequestException(
                    "No puedes quitarte el rol de administrador: el grupo se quedaria sin ninguno. "
                            + "Promueve antes a otro miembro.");
        }

        member.setRole(nuevoRol);
        groupMemberRepository.save(member);

        return toResponse(findGroup(groupId), groupMemberRepository.findByGroupId(groupId));
    }

    /**
     * Saca a un miembro del grupo: uno mismo (salir) o a un tercero
     * (expulsar, solo administradores).
     *
     * <p><b>Regla de negocio central:</b> nadie sale con saldo distinto de
     * cero. Al dejar de ser miembro, sus gastos y sus partes siguen en la
     * base pero desaparecen del informe de balances, que se construye a
     * partir de la lista de miembros. Si su saldo no era cero, los balances
     * de quienes quedan dejan de sumar cero y el dinero se evapora del
     * sistema: alguien deja de cobrar lo que le debian, o de deber lo que
     * debia, sin que nadie lo note.
     *
     * <p>Primero hay que saldar la deuda; despues se puede salir.
     */
    @Transactional
    public void removeMember(Long groupId, Long userId, String requesterEmail) {
        GroupMember requester = groupAccess.requireMember(groupId, requesterEmail);

        boolean seVaElMismo = requester.getUser().getId().equals(userId);

        if (!seVaElMismo && requester.getRole() != GroupRole.ADMIN) {
            throw new ForbiddenException("Solo un administrador puede expulsar a otros miembros");
        }

        GroupMember objetivo = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no pertenece al grupo"));

        exigirSaldoCero(groupId, userId, seVaElMismo);

        long totalMiembros = groupMemberRepository.countByGroupId(groupId);

        // Un grupo sin administrador queda congelado para siempre: nadie
        // podria invitar, expulsar ni editarlo. Solo se permite cuando no
        // queda nadie detras a quien dejar huerfano.
        if (objetivo.getRole() == GroupRole.ADMIN && esElUnicoAdmin(groupId) && totalMiembros > 1) {
            throw new BadRequestException(
                    "Eres el unico administrador: promueve antes a otro miembro.");
        }

        groupMemberRepository.delete(objetivo);
        log.info("Usuario {} sale del grupo {} (accion de {})", userId, groupId, requester.getUser().getId());
    }

    private void exigirSaldoCero(Long groupId, Long userId, boolean seVaElMismo) {
        BigDecimal saldo = balanceService.calculateNetBalances(groupId).stream()
                .filter(b -> b.userId().equals(userId))
                .map(UserBalance::amount)
                .findFirst()
                .orElse(CERO);

        if (saldo.signum() == 0) {
            return;
        }

        String quien = seVaElMismo ? "Tienes" : "Ese miembro tiene";
        String detalle = saldo.signum() > 0
                ? quien + " un saldo pendiente de cobro de " + saldo.abs()
                : quien + " una deuda pendiente de " + saldo.abs();

        throw new BadRequestException(detalle
                + ". Hay que saldar las cuentas antes de salir del grupo.");
    }

    /**
     * Normaliza la moneda pedida o recurre a la de por defecto. La validez
     * del codigo ya la ha comprobado @ValidCurrency antes de llegar aqui.
     */
    private String resolverMoneda(String solicitada) {
        return (solicitada == null || solicitada.isBlank())
                ? defaultCurrency
                : solicitada.trim().toUpperCase();
    }

    private boolean esElUnicoAdmin(Long groupId) {
        return groupMemberRepository.countByGroupIdAndRole(groupId, GroupRole.ADMIN) <= 1;
    }

    private Group findGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));
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
                .currency(group.getCurrency())
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
