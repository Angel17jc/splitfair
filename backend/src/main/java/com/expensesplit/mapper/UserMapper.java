package com.expensesplit.mapper;

import com.expensesplit.dto.response.UserResponse;
import com.expensesplit.model.User;
import org.mapstruct.Mapper;

/**
 * Usuario a su perfil publico.
 *
 * <p>El hash de la contrasena queda fuera por omision: la politica de origen
 * ignora los campos no mapeados, de modo que anadir un dato sensible a la
 * entidad no lo filtra a la API por accidente.
 */
@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    UserResponse toResponse(User user);
}
