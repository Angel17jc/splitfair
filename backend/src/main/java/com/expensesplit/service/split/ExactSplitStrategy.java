package com.expensesplit.service.split;

import com.expensesplit.exception.BadRequestException;
import com.expensesplit.model.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Importe exacto por participante.
 *
 * <p>No hay nada que repartir: los importes vienen dados. Lo unico que hace
 * falta es comprobar que cuadran, porque si no lo hacen los balances del
 * grupo dejan de sumar cero y el fallo aflora mucho despues, en forma de
 * liquidaciones que nunca terminan de saldar la deuda.
 *
 * <p>El mensaje de error dice cuanto falta o cuanto sobra: con solo "no
 * cuadra", quien introduce cinco importes a mano tiene que recalcularlos
 * todos para encontrar el suyo.
 */
@Component
public class ExactSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EXACT;
    }

    @Override
    public List<BigDecimal> distribute(BigDecimal total, List<SplitInput> entries) {
        List<BigDecimal> importes = entries.stream()
                .map(e -> valorDe(e).setScale(2, RoundingMode.HALF_UP))
                .toList();

        BigDecimal suma = importes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal esperado = total.setScale(2, RoundingMode.HALF_UP);

        if (suma.compareTo(esperado) != 0) {
            BigDecimal diferencia = esperado.subtract(suma);
            String ajuste = diferencia.signum() > 0
                    ? "Faltan " + diferencia
                    : "Sobran " + diferencia.abs();

            throw new BadRequestException("Los importes indicados suman " + suma
                    + " y el gasto es de " + esperado + ". " + ajuste + ".");
        }
        return importes;
    }

    private BigDecimal valorDe(SplitInput entrada) {
        if (entrada.value() == null) {
            throw new BadRequestException("Falta el importe de algun participante");
        }
        if (entrada.value().signum() < 0) {
            throw new BadRequestException("Ningun importe puede ser negativo");
        }
        return entrada.value();
    }
}
