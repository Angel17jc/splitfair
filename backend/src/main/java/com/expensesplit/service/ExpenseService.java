package com.expensesplit.service;

import com.expensesplit.dto.request.CreateExpenseRequest;
import com.expensesplit.dto.request.UpdateExpenseRequest;
import com.expensesplit.dto.response.BalanceResponse;
import com.expensesplit.dto.response.ExpenseResponse;
import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ForbiddenException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.*;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;
    private final DebtSimplificationService debtSimplificationService;
    private final GroupAccessService groupAccess;

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request, String payerEmail) {
        groupAccess.requireMember(groupId, payerEmail);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        User paidBy = userRepository.findByEmail(payerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<User> participants = resolverParticipantes(groupId, request.getSplitBetweenUserIds());

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(request.getDescription())
                .amount(request.getAmount())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .build();

        aplicarReparto(expense, participants);
        expenseRepository.save(expense);

        return toResponse(expense);
    }

    /**
     * Modifica un gasto y recalcula su reparto.
     *
     * <p>Solo quien lo pago o un administrador del grupo: el autor corrige
     * sus propios errores, y el administrador arregla los ajenos cuando hace
     * falta.
     */
    @Transactional
    public ExpenseResponse updateExpense(Long expenseId, UpdateExpenseRequest request,
                                           String requesterEmail) {
        Expense expense = buscarConPermiso(expenseId, requesterEmail);
        Long groupId = expense.getGroup().getId();

        List<User> participants = resolverParticipantes(groupId, request.getSplitBetweenUserIds());

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());

        // Los splits anteriores se borran ANTES de insertar los nuevos, con un
        // flush explicito de por medio. Sin el, Hibernate podria emitir los
        // INSERT antes que los DELETE dentro del mismo flush y chocar con la
        // restriccion UNIQUE(expense_id, user_id).
        expense.getSplits().clear();
        expenseRepository.saveAndFlush(expense);

        aplicarReparto(expense, participants);
        expenseRepository.save(expense);

        return toResponse(expense);
    }

    /**
     * Elimina un gasto. Sus partes se van con el por cascada, y los balances
     * del grupo se recalculan solos: se derivan de los gastos vigentes, no de
     * un acumulado que hubiera que corregir.
     */
    @Transactional
    public void deleteExpense(Long expenseId, String requesterEmail) {
        expenseRepository.delete(buscarConPermiso(expenseId, requesterEmail));
    }

    /**
     * Carga el gasto y comprueba que quien pide puede modificarlo.
     *
     * <p>El identificador del gasto llega suelto en la URL, sin el grupo, asi
     * que el grupo se deduce del propio gasto y se valida la pertenencia
     * sobre el. De otro modo bastaria iterar identificadores para editar los
     * gastos de cualquiera.
     */
    private Expense buscarConPermiso(Long expenseId, String requesterEmail) {
        Expense expense = expenseRepository.findWithDetailsById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));

        GroupMember member = groupAccess.requireMember(expense.getGroup().getId(), requesterEmail);

        boolean loPago = expense.getPaidBy().getId().equals(member.getUser().getId());

        if (!loPago && member.getRole() != GroupRole.ADMIN) {
            throw new ForbiddenException(
                    "Solo quien registro el gasto o un administrador pueden modificarlo");
        }
        return expense;
    }

    /**
     * Determina entre quienes se reparte, validando que todos pertenezcan al
     * grupo. Un identificador ajeno se cae en el filtro, de modo que despues
     * se comprueba que no falte ninguno de los pedidos: pasarlo por alto en
     * silencio repartiria el gasto entre menos gente de la indicada.
     */
    private List<User> resolverParticipantes(Long groupId, List<Long> solicitados) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            throw new BadRequestException("El grupo no tiene miembros");
        }

        if (solicitados == null) {
            return members.stream().map(GroupMember::getUser).toList();
        }

        List<User> participants = members.stream()
                .map(GroupMember::getUser)
                .filter(u -> solicitados.contains(u.getId()))
                .toList();

        if (participants.isEmpty()) {
            throw new BadRequestException("Debe haber al menos un participante en el gasto");
        }
        if (participants.size() != solicitados.stream().distinct().count()) {
            throw new BadRequestException("Algun participante indicado no pertenece al grupo");
        }
        return participants;
    }

    /**
     * Reparte el importe entre los participantes y cuelga las partes del
     * gasto.
     *
     * <p>Se ordenan por id para que el reparto del centimo sobrante sea
     * estable: recalcular el mismo gasto no debe cambiar a quien le toca
     * pagar de mas.
     */
    private void aplicarReparto(Expense expense, List<User> participants) {
        List<User> ordered = participants.stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();

        List<BigDecimal> shares = MoneySplitter.splitEqually(expense.getAmount(), ordered.size());

        for (int i = 0; i < ordered.size(); i++) {
            expense.getSplits().add(ExpenseSplit.builder()
                    .expense(expense)
                    .user(ordered.get(i))
                    .amountOwed(shares.get(i))
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getGroupExpenses(Long groupId, String requesterEmail) {
        groupAccess.requireMember(groupId, requesterEmail);

        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> getGroupBalances(Long groupId, String requesterEmail) {
        groupAccess.requireMember(groupId, requesterEmail);

        return balanceService.calculateNetBalances(groupId).stream()
                .map(b -> BalanceResponse.builder()
                        .userId(b.userId())
                        .userName(b.userName())
                        .netBalance(b.amount())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SettlementSuggestionResponse> getSuggestedSettlements(Long groupId, String requesterEmail) {
        groupAccess.requireMember(groupId, requesterEmail);

        return debtSimplificationService.simplify(balanceService.calculateNetBalances(groupId));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .paidByName(expense.getPaidBy().getName())
                .splits(expense.getSplits().stream().map(s -> ExpenseResponse.SplitResponse.builder()
                        .userId(s.getUser().getId())
                        .userName(s.getUser().getName())
                        .amountOwed(s.getAmountOwed())
                        .build()).toList())
                .build();
    }
}
