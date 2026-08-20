package com.expensesplit.service;

import com.expensesplit.exception.ForbiddenException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.GroupMember;
import com.expensesplit.model.GroupRole;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto unico de control de acceso a los grupos.
 *
 * <p>Spring Security responde a "quien eres" (el JWT es valido), pero no a
 * "sobre que tienes derecho". Sin esta comprobacion, cualquier usuario
 * autenticado podia leer y modificar los gastos de cualquier grupo
 * simplemente iterando identificadores, porque son BIGINT secuenciales.
 *
 * <p>La logica vive en un solo sitio a proposito: repartir comprobaciones
 * por los servicios garantiza que tarde o temprano un endpoint nuevo se
 * olvide de una, y ese olvido no falla ningun test, simplemente deja la
 * puerta abierta.
 */
@Service
@RequiredArgsConstructor
public class GroupAccessService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    /**
     * Exige que el usuario pertenezca al grupo.
     *
     * @return la pertenencia, util cuando quien llama necesita el rol
     * @throws ResourceNotFoundException si el grupo no existe
     * @throws ForbiddenException        si existe pero el usuario no es miembro
     */
    @Transactional(readOnly = true)
    public GroupMember requireMember(Long groupId, String email) {
        return groupMemberRepository.findByGroupIdAndUserEmail(groupId, email)
                .orElseThrow(() -> {
                    // Se distingue "no existe" de "no es tuyo" para que los
                    // errores sean diagnosticables. El unico dato que eso
                    // revela es la existencia del grupo, nunca su contenido.
                    if (!groupRepository.existsById(groupId)) {
                        return new ResourceNotFoundException("Grupo no encontrado");
                    }
                    return new ForbiddenException("No perteneces a este grupo");
                });
    }

    /**
     * Exige que el usuario sea administrador del grupo. Se aplica a las
     * acciones que alteran su composicion: invitar, expulsar, editar.
     */
    @Transactional(readOnly = true)
    public GroupMember requireAdmin(Long groupId, String email) {
        GroupMember member = requireMember(groupId, email);

        if (member.getRole() != GroupRole.ADMIN) {
            throw new ForbiddenException("Esta accion requiere ser administrador del grupo");
        }
        return member;
    }
}
