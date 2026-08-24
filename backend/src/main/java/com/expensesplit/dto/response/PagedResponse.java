package com.expensesplit.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de paginacion propio de la API.
 *
 * <p>Se evita a proposito serializar directamente el {@code Page} de Spring
 * Data: su forma JSON no forma parte de su contrato publico (Spring Boot 3.3
 * lo advierte al arrancar) y ha cambiado entre versiones. Exponerlo ataria el
 * contrato de la API a un detalle interno del framework.
 *
 * @param <T> tipo de los elementos de la pagina
 */
@Getter
@Builder
public class PagedResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    /** Construye la respuesta transformando cada elemento de la pagina. */
    public static <S, T> PagedResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return PagedResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
