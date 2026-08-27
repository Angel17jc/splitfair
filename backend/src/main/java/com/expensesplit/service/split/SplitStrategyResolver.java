package com.expensesplit.service.split;

import com.expensesplit.model.SplitType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Localiza la estrategia correspondiente a cada tipo de reparto.
 *
 * <p>Spring inyecta todas las implementaciones disponibles, de modo que
 * anadir un tipo nuevo consiste en crear una clase: no hay ningun switch que
 * actualizar ni ningun registro que mantener al dia.
 */
@Component
public class SplitStrategyResolver {

    private final Map<SplitType, SplitStrategy> strategies;

    public SplitStrategyResolver(List<SplitStrategy> disponibles) {
        this.strategies = disponibles.stream()
                .collect(Collectors.toMap(SplitStrategy::type, Function.identity()));

        // Si algun tipo del enum se queda sin implementacion, el fallo debe
        // impedir el arranque y no esperar a la primera peticion que lo use.
        for (SplitType tipo : SplitType.values()) {
            if (!strategies.containsKey(tipo)) {
                throw new IllegalStateException(
                        "No hay estrategia de reparto para el tipo " + tipo);
            }
        }
    }

    public SplitStrategy forType(SplitType tipo) {
        return strategies.get(tipo);
    }
}
