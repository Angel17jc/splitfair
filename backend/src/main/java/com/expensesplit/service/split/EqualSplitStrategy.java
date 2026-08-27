package com.expensesplit.service.split;

import com.expensesplit.model.SplitType;
import com.expensesplit.service.MoneySplitter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Partes iguales, con el centimo sobrante repartido por mayor residuo. */
@Component
public class EqualSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EQUAL;
    }

    @Override
    public List<BigDecimal> distribute(BigDecimal total, List<SplitInput> entries) {
        return MoneySplitter.splitEqually(total, entries.size());
    }
}
