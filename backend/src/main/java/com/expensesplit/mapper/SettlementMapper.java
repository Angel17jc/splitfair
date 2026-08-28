package com.expensesplit.mapper;

import com.expensesplit.dto.response.SettlementResponse;
import com.expensesplit.model.Settlement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Liquidacion a su representacion en la API. */
@Mapper(config = CentralMapperConfig.class)
public interface SettlementMapper {

    @Mapping(target = "paidByUserId", source = "paidBy.id")
    @Mapping(target = "paidByName", source = "paidBy.name")
    @Mapping(target = "paidToUserId", source = "paidTo.id")
    @Mapping(target = "paidToName", source = "paidTo.name")
    @Mapping(target = "currency", source = "group.currency")
    SettlementResponse toResponse(Settlement settlement);
}
