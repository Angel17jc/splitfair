package com.expensesplit.service;

import com.expensesplit.dto.request.CreateExpenseRequest;
import com.expensesplit.dto.response.BalanceResponse;
import com.expensesplit.dto.response.ExpenseResponse;
import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.exception.BadRequestException;
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

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            throw new BadRequestException("El grupo no tiene miembros");
        }

        // Si no se especifica entre quienes se divide, se reparte entre todos los miembros del grupo
        List<User> participants = request.getSplitBetweenUserIds() == null
                ? members.stream().map(GroupMember::getUser).toList()
                : members.stream()
                    .map(GroupMember::getUser)
                    .filter(u -> request.getSplitBetweenUserIds().contains(u.getId()))
                    .toList();

        if (participants.isEmpty()) {
            throw new BadRequestException("Debe haber al menos un participante en el gasto");
        }

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(request.getDescription())
                .amount(request.getAmount())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .build();

        // Los participantes se ordenan por id para que el reparto del centimo
        // sobrante sea estable: recalcular el mismo gasto no debe cambiar a
        // quien le toca pagar de mas.
        List<User> ordered = participants.stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();

        List<BigDecimal> shares = MoneySplitter.splitEqually(request.getAmount(), ordered.size());

        for (int i = 0; i < ordered.size(); i++) {
            ExpenseSplit split = ExpenseSplit.builder()
                    .expense(expense)
                    .user(ordered.get(i))
                    .amountOwed(shares.get(i))
                    .build();
            expense.getSplits().add(split);
        }

        expenseRepository.save(expense);

        return toResponse(expense);
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
