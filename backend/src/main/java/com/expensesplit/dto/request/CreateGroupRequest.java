package com.expensesplit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateGroupRequest {

    @NotBlank
    private String name;

    private String description;
}
