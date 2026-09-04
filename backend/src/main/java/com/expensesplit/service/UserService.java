package com.expensesplit.service;

import com.expensesplit.dto.request.ChangePasswordRequest;
import com.expensesplit.dto.request.DeleteAccountRequest;
import com.expensesplit.dto.request.UpdateProfileRequest;
import com.expensesplit.dto.response.UserResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.mapper.UserMapper;
import com.expensesplit.model.User;
import com.expensesplit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** Lo que ven los demas miembros del grupo en lugar del nombre real. */
    private static final String NOMBRE_ANONIMO = "Usuario eliminado";

    /** Dominio reservado por la RFC 2606: no resuelve ni puede registrarse. */
    private static final String DOMINIO_NO_ENRUTABLE = "invalid";


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        return userMapper.toResponse(findByEmail(email));
    }

    @Transactional
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmail(email);
        user.setName(request.getName().trim());

        return userMapper.toResponse(userRepository.save(user));
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

    /**
     * Da de baja la cuenta anonimizandola.
     *
     * <p><b>No se borra la fila</b>, y no es una simplificacion: el usuario
     * aparece en gastos, en repartos y en liquidaciones confirmadas.
     * Borrarlo dejaria apuntes sin dueno y, sobre todo, los balances del
     * grupo dejarian de sumar cero —el dinero se evaporaria del informe sin
     * que nadie hubiera pagado nada—. Es el mismo motivo por el que nadie
     * puede salir de un grupo con saldo distinto de cero.
     *
     * <p>Lo que se elimina son los datos personales: nombre y correo se
     * sustituyen, y el hash de contrasena se reemplaza por uno derivado de un
     * valor aleatorio que nadie conoce, de modo que la cuenta queda
     * inutilizable aunque la fila siga ahi. Para el resto del grupo, esa
     * persona pasa a ser "Usuario eliminado" en un historico que sigue
     * cuadrando.
     *
     * <p>El correo nuevo lleva un identificador aleatorio y el dominio
     * reservado {@code .invalid} (RFC 2606), que por definicion no resuelve.
     * Hacen falta las dos cosas: aleatorio porque la columna es unica y dos
     * bajas chocarian, y no enrutable para que nunca pueda enviarse un correo
     * a esa direccion por error. El correo original queda libre, asi que la
     * persona puede volver a registrarse; sera una cuenta nueva, sin relacion
     * con los apuntes anteriores.
     *
     * <p>Efecto colateral buscado: los access token ya emitidos llevan el
     * correo como sujeto, asi que al cambiarlo dejan de resolver a ningun
     * usuario y caducan de inmediato en vez de seguir sirviendo sus quince
     * minutos. Las sesiones se revocan ademas explicitamente.
     */
    @Transactional
    public void deleteAccount(String email, DeleteAccountRequest request) {
        User user = findByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contrasena actual no es correcta");
        }

        if (user.getDeletedAt() != null) {
            throw new BadRequestException("La cuenta ya esta dada de baja");
        }

        Long id = user.getId();

        user.setName(NOMBRE_ANONIMO);
        user.setEmail("eliminado-" + UUID.randomUUID() + "@" + DOMINIO_NO_ENRUTABLE);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        refreshTokenService.revokeAllSessions(id);

        // Se registra el identificador, no el correo: escribir el correo en el
        // log al darlo de baja conservaria en los logs justo el dato que se
        // acaba de eliminar de la base.
        log.info("Cuenta {} dada de baja y anonimizada; sesiones revocadas", id);
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

}
