package com.expensesplit.service;

import com.expensesplit.dto.request.CreateExpenseRequest;
import com.expensesplit.dto.request.SplitEntryRequest;
import com.expensesplit.dto.request.UpdateExpenseRequest;
import com.expensesplit.dto.response.BalanceResponse;
import com.expensesplit.dto.response.GroupBalanceResponse;
import com.expensesplit.dto.response.PagedResponse;
import com.expensesplit.dto.response.ExpenseResponse;
import com.expensesplit.dto.response.SettlementSuggestionResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ForbiddenException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.mapper.ExpenseMapper;
import com.expensesplit.model.*;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.service.split.SplitInput;
import com.expensesplit.service.split.SplitStrategyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final SplitStrategyResolver splitStrategyResolver;
    private final ExpenseMapper expenseMapper;

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request, String payerEmail) {
        groupAccess.requireMember(groupId, payerEmail);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        User paidBy = userRepository.findByEmail(payerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        RepartoPreparado reparto = prepararReparto(groupId, request.getSplitType(),
                request.getSplits(), request.getSplitBetweenUserIds());

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(request.getDescription())
                .amount(request.getAmount())
                .category(categoriaDe(request.getCategory()))
                .expenseDate(request.getExpenseDate())
                .build();

        aplicarReparto(expense, reparto);
        expenseRepository.save(expense);

        return expenseMapper.toResponse(expense);
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

        RepartoPreparado reparto = prepararReparto(groupId, request.getSplitType(),
                request.getSplits(), request.getSplitBetweenUserIds());

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCategory(categoriaDe(request.getCategory()));
        expense.setExpenseDate(request.getExpenseDate());

        // Los splits anteriores se borran ANTES de insertar los nuevos, con un
        // flush explicito de por medio. Sin el, Hibernate podria emitir los
        // INSERT antes que los DELETE dentro del mismo flush y chocar con la
        // restriccion UNIQUE(expense_id, user_id).
        expense.getSplits().clear();
        expenseRepository.saveAndFlush(expense);

        aplicarReparto(expense, reparto);
        expenseRepository.save(expense);

        return expenseMapper.toResponse(expense);
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
     * Resuelve quienes participan y con que valor, validando que todos
     * pertenezcan al grupo.
     *
     * <p>Admite las dos formas de indicar participantes: la lista detallada
     * {@code splits}, necesaria para los repartos personalizados, y la
     * abreviada {@code splitBetweenUserIds}, que solo tiene sentido en el
     * reparto igual porque no lleva valores.
     */
    private RepartoPreparado prepararReparto(Long groupId, SplitType tipoPedido,
                                              List<SplitEntryRequest> splits,
                                              List<Long> splitBetweenUserIds) {

        SplitType tipo = tipoPedido == null ? SplitType.EQUAL : tipoPedido;

        Map<Long, User> miembros = groupMemberRepository.findByGroupId(groupId).stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), GroupMember::getUser,
                        (a, b) -> a, LinkedHashMap::new));

        if (miembros.isEmpty()) {
            throw new BadRequestException("El grupo no tiene miembros");
        }

        List<SplitInput> entradas = construirEntradas(tipo, splits, splitBetweenUserIds, miembros);

        if (entradas.isEmpty()) {
            throw new BadRequestException("Debe haber al menos un participante en el gasto");
        }

        // Un participante repetido duplicaria su parte y descuadraria el
        // gasto sin que ninguna estrategia lo detectase.
        if (entradas.stream().map(SplitInput::userId).distinct().count() != entradas.size()) {
            throw new BadRequestException("Hay participantes repetidos en el reparto");
        }

        for (SplitInput entrada : entradas) {
            if (entrada.userId() == null || !miembros.containsKey(entrada.userId())) {
                // Ignorarlo en silencio repartiria el gasto entre menos gente
                // de la indicada, y el cliente creeria que se aplico.
                throw new BadRequestException("Algun participante indicado no pertenece al grupo");
            }
        }

        // Orden estable por id: asi el centimo sobrante recae siempre en los
        // mismos, y recalcular el gasto no cambia a quien le toca.
        List<SplitInput> ordenadas = entradas.stream()
                .sorted(Comparator.comparing(SplitInput::userId))
                .toList();

        return new RepartoPreparado(tipo, ordenadas, miembros);
    }

    private List<SplitInput> construirEntradas(SplitType tipo,
                                                List<SplitEntryRequest> splits,
                                                List<Long> splitBetweenUserIds,
                                                Map<Long, User> miembros) {
        if (splits != null && !splits.isEmpty()) {
            return splits.stream()
                    .map(e -> new SplitInput(e.getUserId(), e.getValue()))
                    .toList();
        }

        if (tipo != SplitType.EQUAL) {
            throw new BadRequestException("El reparto " + tipo
                    + " exige indicar el valor de cada participante en 'splits'");
        }

        List<Long> ids = splitBetweenUserIds != null
                ? splitBetweenUserIds
                : List.copyOf(miembros.keySet());

        return ids.stream().map(id -> new SplitInput(id, null)).toList();
    }

    /**
     * Reparte el importe segun la estrategia y cuelga las partes del gasto.
     */
    private void aplicarReparto(Expense expense, RepartoPreparado reparto) {
        List<SplitInput> entradas = reparto.entradas();

        List<BigDecimal> importes = splitStrategyResolver.forType(reparto.tipo())
                .distribute(expense.getAmount(), entradas);

        for (int i = 0; i < entradas.size(); i++) {
            expense.getSplits().add(ExpenseSplit.builder()
                    .expense(expense)
                    .user(reparto.usuarios().get(entradas.get(i).userId()))
                    .amountOwed(importes.get(i))
                    .build());
        }
        expense.setSplitType(reparto.tipo());
    }

    /**
     * Un gasto sin categoria queda como OTROS.
     *
     * <p>Se resuelve aqui y no con @Builder.Default en la entidad porque
     * Lombok solo aplica ese valor cuando el campo se omite del builder: al
     * pasarle explicitamente el null de la peticion, lo asigna tal cual y la
     * columna NOT NULL revienta al guardar.
     */
    private ExpenseCategory categoriaDe(ExpenseCategory solicitada) {
        return solicitada == null ? ExpenseCategory.OTROS : solicitada;
    }

    /** Reparto ya validado, listo para aplicarse. */
    private record RepartoPreparado(SplitType tipo,
                                    List<SplitInput> entradas,
                                    Map<Long, User> usuarios) {
    }

    /**
     * Gastos del grupo, paginados y con los filtros que vengan informados.
     *
     * <p>Se resuelve en dos consultas y no en una. Paginar directamente una
     * consulta que ademas trae los splits obligaria a Hibernate a cargar
     * todas las filas del grupo y paginar en memoria; con suficientes gastos
     * eso se lleva el servidor por delante. Aqui la base pagina los
     * identificadores con LIMIT y OFFSET, y despues se hidratan unicamente
     * los gastos de la pagina.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ExpenseResponse> getGroupExpenses(Long groupId, ExpenseFilter filtro,
                                                            Pageable pageable, String requesterEmail) {
        groupAccess.requireMember(groupId, requesterEmail);

        Page<Long> ids = expenseRepository.findIdsByFilters(groupId, filtro.category(),
                filtro.from(), filtro.to(), filtro.paidBy(), pageable);

        if (ids.isEmpty()) {
            return PagedResponse.from(ids, id -> null);
        }

        // El IN no garantiza ningun orden, asi que se reordena segun la
        // pagina de identificadores, que si viene ordenada por fecha.
        Map<Long, Expense> porId = expenseRepository.findByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Expense::getId, e -> e));

        return PagedResponse.from(ids, id -> expenseMapper.toResponse(porId.get(id)));
    }

    /**
     * Filtros opcionales del listado de gastos. Un campo nulo no restringe.
     *
     * @param category categoria exacta
     * @param from     fecha minima, inclusive
     * @param to       fecha maxima, inclusive
     * @param paidBy   quien adelanto el dinero
     */
    public record ExpenseFilter(ExpenseCategory category, LocalDate from,
                                 LocalDate to, Long paidBy) {

        public static ExpenseFilter none() {
            return new ExpenseFilter(null, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public GroupBalanceResponse getGroupBalances(Long groupId, String requesterEmail) {
        // El guardian ya devuelve la pertenencia con su grupo cargado: pedirlo
        // otra vez seria una consulta de mas en cada lectura de balances.
        Group grupo = groupAccess.requireMember(groupId, requesterEmail).getGroup();

        List<UserBalance> balances = balanceService.calculateNetBalances(groupId);

        // El gasto total del grupo es la suma de lo adelantado por todos: no
        // hace falta una consulta extra para obtenerlo.
        BigDecimal totalGastado = balances.stream()
                .map(UserBalance::totalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return GroupBalanceResponse.builder()
                .currency(grupo.getCurrency())
                .totalSpent(totalGastado)
                .balances(balances.stream()
                        .map(b -> BalanceResponse.builder()
                                .userId(b.userId())
                                .userName(b.userName())
                                .totalPaid(b.totalPaid())
                                .totalOwed(b.totalOwed())
                                .settlementsPaid(b.settlementsPaid())
                                .settlementsReceived(b.settlementsReceived())
                                .netBalance(b.amount())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SettlementSuggestionResponse> getSuggestedSettlements(Long groupId, String requesterEmail) {
        groupAccess.requireMember(groupId, requesterEmail);

        return debtSimplificationService.simplify(balanceService.calculateNetBalances(groupId));
    }

}
