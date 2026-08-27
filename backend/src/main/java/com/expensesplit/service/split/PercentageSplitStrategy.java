package com.expensesplit.service.split;

import com.expensesplit.exception.BadRequestException;
import com.expensesplit.model.SplitType;
import com.expensesplit.service.MoneySplitter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Porcentaje por participante. Deben sumar exactamente 100.
 *
 * <p>Los porcentajes no se convierten a importe uno a uno, sino que se usan
 * como pesos del reparto por mayor residuo. La diferencia importa: calcular
 * cada parte por separado y redondear produce descuadres en cuanto los
 * porcentajes tienen decimales, exactamente el mismo fallo que la Fase 0
 * corrigio en el reparto igual. Repartiendo por pesos, la suma de las partes
 * es siempre el total.
 */
@Component
public class PercentageSplitStrategy implements SplitStrategy {

    private static final BigDecimal CIEN = new BigDecimal("100");

    @Override
    public SplitType type() {
        return SplitType.PERCENTAGE;
    }

    @Override
    public List<BigDecimal> distribute(BigDecimal total, List<SplitInput> entries) {
        List<BigDecimal> porcentajes = entries.stream().map(this::valorDe).toList();

        BigDecimal suma = porcentajes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (suma.compareTo(CIEN) != 0) {
            throw new BadRequestException("Los porcentajes suman "
                    + suma.stripTrailingZeros().toPlainString()
                    + "% y deben sumar exactamente 100%.");
        }
        return MoneySplitter.splitByWeights(total, porcentajes);
    }

    private BigDecimal valorDe(SplitInput entrada) {
        if (entrada.value() == null) {
            throw new BadRequestException("Falta el porcentaje de algun participante");
        }
        if (entrada.value().signum() < 0) {
            throw new BadRequestException("Ningun porcentaje puede ser negativo");
        }
        return entrada.value();
    }
}
