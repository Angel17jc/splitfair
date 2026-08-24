package com.expensesplit.service;

import com.expensesplit.dto.request.LoginRequest;
import com.expensesplit.dto.request.RegisterRequest;
import com.expensesplit.dto.response.AuthResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.model.User;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final InvitationService invitationService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Ya existe una cuenta con ese email");
        }

        User user = userRepository.save(User.builder()
                .name(request.getName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build());

        Long joinedGroupId = aceptarInvitacionSiLaHay(request.getInvitationToken(), email);

        return issueCredentials(user, joinedGroupId);
    }

    /**
     * Consume el token de invitacion, si el registro traia uno.
     *
     * <p>Ocurre dentro de la transaccion del registro a proposito. Si la
     * invitacion resulta invalida, caducada o dirigida a otra direccion, se
     * revierte tambien la creacion de la cuenta: es preferible que el usuario
     * repita el registro a dejarlo con una cuenta creada, fuera del grupo al
     * que le invitaron y sin entender por que.
     */
    private Long aceptarInvitacionSiLaHay(String invitationToken, String email) {
        if (invitationToken == null || invitationToken.isBlank()) {
            return null;
        }
        return invitationService.accept(invitationToken, email).getId();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        // Delega en Spring Security: comprueba la contrasena con el mismo
        // encoder del registro y lanza AuthenticationException si falla.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        return issueCredentials(user);
    }

    /**
     * Cambia un refresh token por un par de credenciales nuevo. El token
     * presentado queda invalidado en el acto.
     *
     * <p>Sin @Transactional a proposito: la rotacion ya es atomica dentro de
     * RefreshTokenService. Abrir aqui una transaccion externa haria que fuera
     * ESTA la que decide el rollback, anulando el noRollbackFor de la interna
     * y deshaciendo la revocacion por reutilizacion.
     */
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.RotationResult rotacion = refreshTokenService.rotate(refreshToken);

        return buildResponse(rotacion.user(), rotacion.refreshToken());
    }

    /** Cierra la sesion revocando la familia completa de tokens. */
    public void logout(String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
    }

    private AuthResponse issueCredentials(User user) {
        return issueCredentials(user, null);
    }

    private AuthResponse issueCredentials(User user, Long joinedGroupId) {
        return buildResponse(user, refreshTokenService.issue(user), joinedGroupId);
    }

    private AuthResponse buildResponse(User user, String refreshToken) {
        return buildResponse(user, refreshToken, null);
    }

    private AuthResponse buildResponse(User user, String refreshToken, Long joinedGroupId) {
        return AuthResponse.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(user.getEmail()))
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .joinedGroupId(joinedGroupId)
                .build();
    }

    /**
     * Los emails se guardan y comparan en minusculas. Sin esto,
     * "Ana@test.com" y "ana@test.com" crearian dos cuentas distintas pese a
     * ser la misma direccion, y la restriccion UNIQUE no lo impediria.
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
