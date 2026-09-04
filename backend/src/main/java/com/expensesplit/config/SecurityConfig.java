package com.expensesplit.config;

import com.expensesplit.security.JwtAuthFilter;
import com.expensesplit.security.RestAccessDeniedHandler;
import com.expensesplit.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // "/swagger-ui.html" va aparte: es la URL que la gente teclea y
                // NO casa con "/swagger-ui/**", asi que sin ella la
                // documentacion respondia 401 y quedaba inaccesible.
                .requestMatchers("/api/auth/**",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Solo la vista previa de una invitacion es publica: quien
                // abre el link puede no tener cuenta todavia. Se acota al
                // metodo GET para no abrir de paso el resto de operaciones
                // sobre invitaciones.
                .requestMatchers(HttpMethod.GET, "/api/invitations/*").permitAll()
                // Solo las sondas de estado. Se abren porque quien las
                // consulta —Docker, el orquestador, el balanceador— no tiene
                // credenciales, y una sonda que responde 401 se interpreta
                // como servicio caido: el contenedor entraria en un ciclo de
                // reinicios sin que le pase nada.
                //
                // Se usa EndpointRequest y no la ruta escrita a mano porque
                // la base de /actuator es configurable: con un literal,
                // cambiarla dejaria de casar en silencio y las sondas
                // empezarian a fallar sin que nada avise.
                //
                // El resto de endpoints —metricas incluidas— queda bajo la
                // regla general y exige autenticacion. Y en el despliegue,
                // ademas, nginx solo reenvia /api: nada de /actuator es
                // alcanzable desde fuera.
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                .anyRequest().authenticated()
            )
            // Sin esto, los fallos que ocurren en la cadena de filtros salen
            // por la pagina de error de Spring, en HTML y con codigos que no
            // distinguen "no se quien eres" de "no puedes".
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
