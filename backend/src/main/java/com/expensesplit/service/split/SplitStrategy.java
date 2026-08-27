package com.expensesplit.service.split;

import com.expensesplit.model.SplitType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calcula cuanto le corresponde a cada participante de un gasto.
 *
 * <p>Cada tipo de reparto tiene su propia validacion y su propia forma de
 * repartir. Resolverlo con condicionales dentro del servicio de gastos
 * significaria que anadir un tipo nuevo obliga a tocar un metodo que ya hace
 * otras cinco cosas, y que las validaciones de cada modo acaben mezcladas.
 *
 * <p><b>Contrato que toda implementacion debe cumplir:</b> la suma de los
 * importes devueltos es <b>exactamente</b> el total del gasto. Sin eso los
 * balances del grupo dejan de sumar cero.
 */
public interface SplitStrategy {

    SplitType type();

    /**
     * @param total   importe del gasto
     * @param entries participantes con su valor, ya validados como miembros
     * @return importe por participante, en el mismo orden que {@code entries}
     */
    List<BigDecimal> distribute(BigDecimal total, List<SplitInput> entries);
}
