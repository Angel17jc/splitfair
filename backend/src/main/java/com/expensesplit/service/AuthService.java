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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Ya existe una cuenta con ese email");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, java.util.Collections.emptyList());
        String token = jwtTokenProvider.generateToken(authentication);

        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        String token = jwtTokenProvider.generateToken(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail());
    }
}
