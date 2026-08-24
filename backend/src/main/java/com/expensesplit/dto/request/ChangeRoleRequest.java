package com.expensesplit.dto.request;

import com.expensesplit.model.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotNull(message = "El rol es obligatorio")
    private GroupRole role;
}
