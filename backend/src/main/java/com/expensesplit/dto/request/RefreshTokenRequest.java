package com.expensesplit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "El token de refresco es obligatorio")
    private String refreshToken;
}
