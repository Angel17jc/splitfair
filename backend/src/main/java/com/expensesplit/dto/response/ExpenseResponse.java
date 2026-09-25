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

        /**
         * Lo que se indico al repartir: porcentaje, partes o importe exacto,
         * segun el {@code splitType} del gasto.
         *
         * <p>Es el mismo dato que se envia en `splits[].value` al crear o
         * editar, devuelto tal cual para que el formulario de edicion pueda
         * reconstruir el reparto en vez de deducirlo de los importes, que solo
         * sale bien cuando la division es exacta.
         *
         * <p>Nulo en EQUAL y en los gastos anteriores a la migracion V9.
         */
        private BigDecimal value;
    }
}
