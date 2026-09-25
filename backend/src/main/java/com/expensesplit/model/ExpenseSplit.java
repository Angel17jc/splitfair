package com.expensesplit.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_splits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Monto que le corresponde pagar a este usuario de este gasto especifico
    @Column(name = "amount_owed", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountOwed;

    /**
     * Valor que el cliente indico para esta parte, o null.
     *
     * <p>Segun el {@code splitType} del gasto es un porcentaje, un numero de
     * partes o un importe exacto. Se conserva porque sin el no se puede
     * <b>reeditar</b> el gasto sin degradarlo: con un reparto al 70/30,
     * cambiar el importe total exige los porcentajes para recalcularlo.
     *
     * <p>Nulo en EQUAL —donde no hay valor que indicar— y en los gastos
     * creados antes de la migracion V9.
     */
    @Column(name = "split_value", precision = 12, scale = 4)
    private BigDecimal splitValue;
}
