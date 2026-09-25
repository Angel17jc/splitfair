package com.expensesplit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private String description;
    private BigDecimal amount;
    private String category;
    private String splitType;
    private LocalDate expenseDate;
    /**
     * Quien pago, por identificador.
     *
     * <p>Hasta ahora solo viajaba el nombre, y con el nombre no se puede
     * decidir nada: dos miembros del mismo grupo pueden llamarse igual, asi
     * que el cliente no tenia forma fiable de saber si el usuario actual pago
     * un gasto. De eso depende ofrecer o no los botones de editar y borrar,
     * que el backend ya restringe al pagador o a un administrador.
     */
    private Long paidByUserId;

    private String paidByName;
    private List<SplitResponse> splits;

    @Data
    @Builder
    @AllArgsConstructor
    public static class SplitResponse {
        private Long userId;
        private String userName;
        private BigDecimal amountOwed;
    }
}
