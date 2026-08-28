package com.expensesplit.mapper;

import com.expensesplit.dto.response.ExpenseResponse;
import com.expensesplit.model.Expense;
import com.expensesplit.model.ExpenseSplit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Gasto a su representacion en la API.
 *
 * <p>Se exponen nombres y no entidades anidadas: la respuesta de un gasto no
 * debe arrastrar el usuario completo de cada participante, con su email y su
 * fecha de alta.
 */
@Mapper(config = CentralMapperConfig.class)
public interface ExpenseMapper {

    @Mapping(target = "paidByName", source = "paidBy.name")
    ExpenseResponse toResponse(Expense expense);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.name")
    ExpenseResponse.SplitResponse toSplitResponse(ExpenseSplit split);

    List<ExpenseResponse.SplitResponse> toSplitResponses(List<ExpenseSplit> splits);
}
