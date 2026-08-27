package com.expensesplit.service.split;

import com.expensesplit.exception.BadRequestException;
import com.expensesplit.model.SplitType;
import com.expensesplit.service.MoneySplitter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Partes proporcionales: quien tiene 2 partes paga el doble que quien tiene 1.
 *
 * <p>Util cuando el reparto no es igual pero tampoco se quiere calcular a
 * mano: una habitacion doble frente a dos individuales, o quien se queda dos
 * noches de tres.
 *
 * <p>A diferencia de los porcentajes, las partes no tienen que sumar ninguna
 * cantidad concreta: lo unico que importa es su proporcion.
 */
@Component
public class SharesSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.SHARES;
    }

    @Override
    public List<BigDecimal> distribute(BigDecimal total, List<SplitInput> entries) {
        List<BigDecimal> partes = entries.stream().map(this::valorDe).toList();

        if (partes.stream().allMatch(p -> p.signum() == 0)) {
            throw new BadRequestException("Al menos un participante debe tener alguna parte");
        }
        return MoneySplitter.splitByWeights(total, partes);
    }

    private BigDecimal valorDe(SplitInput entrada) {
        if (entrada.value() == null) {
            throw new BadRequestException("Falta el numero de partes de algun participante");
        }
        if (entrada.value().signum() < 0) {
            throw new BadRequestException("El numero de partes no puede ser negativo");
        }
        return entrada.value();
    }
}
