package com.expensesplit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Perfil publico del usuario autenticado. No incluye el hash de la
 * contrasena ni ningun dato interno.
 */
@Data
@Builder
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
}
