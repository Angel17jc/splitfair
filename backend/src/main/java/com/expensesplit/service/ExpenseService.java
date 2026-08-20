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
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;
    private final DebtSimplificationService debtSimplificationService;

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request, String payerEmail) {
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

    public List<ExpenseResponse> getGroupExpenses(Long groupId) {
        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId)
                .stream().map(this::toResponse).toList();
    }

    public List<BalanceResponse> getGroupBalances(Long groupId) {
        Map<User, BigDecimal> balances = balanceService.calculateNetBalances(groupId);

        return balances.entrySet().stream()
                .map(e -> BalanceResponse.builder()
                        .userId(e.getKey().getId())
                        .userName(e.getKey().getName())
                        .netBalance(e.getValue().setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();
    }

    public List<SettlementSuggestionResponse> getSuggestedSettlements(Long groupId) {
        Map<User, BigDecimal> balances = balanceService.calculateNetBalances(groupId);
        return debtSimplificationService.simplify(balances);
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
