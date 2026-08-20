package com.expensesplit.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Igualdad por identificador. Sin esto, dos instancias del mismo usuario
     * cargadas en sesiones distintas (o un proxy perezoso frente a la entidad
     * ya inicializada) se consideran usuarios diferentes al meterlas en un
     * Set o usarlas como clave de un Map.
     *
     * <p>Se compara con Hibernate.getClass para que un proxy perezoso sea
     * igual a la entidad real, y se exige id no nulo: dos entidades aun sin
     * persistir no son la misma.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        User other = (User) o;
        return id != null && id.equals(other.getId());
    }

    /**
     * Constante a proposito: el id lo asigna la base al insertar, de modo que
     * un hashCode derivado de el cambiaria despues de guardar y la entidad se
     * perderia dentro de cualquier coleccion basada en hash.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
