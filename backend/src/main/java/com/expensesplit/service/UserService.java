package com.expensesplit.service;

import com.expensesplit.dto.request.ChangePasswordRequest;
import com.expensesplit.dto.request.UpdateProfileRequest;
import com.expensesplit.dto.response.UserResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.User;
import com.expensesplit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        return toResponse(findByEmail(email));
    }

    @Transactional
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmail(email);
        user.setName(request.getName().trim());

        return toResponse(userRepository.save(user));
    }

    /**
     * Cambia la contrasena y cierra todas las sesiones abiertas.
     *
     * <p>La revocacion es deliberada: quien cambia su contrasena suele
     * hacerlo porque sospecha que alguien mas tiene acceso. Si las sesiones
     * existentes siguieran vivas, el cambio no serviria de nada, porque el
     * intruso conserva un refresh token valido durante treinta dias.
     *
     * <p>Se revocan tambien las del propio usuario, incluida la que ejecuta
     * el cambio. Es una molestia menor a cambio de una garantia clara: tras
     * cambiar la contrasena, nadie sigue dentro con las credenciales
     * anteriores.
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contrasena actual no es correcta");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contrasena nueva debe ser distinta de la actual");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenService.revokeAllSessions(user.getId());
        log.info("Contrasena cambiada para el usuario {}; sesiones revocadas", user.getId());
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
