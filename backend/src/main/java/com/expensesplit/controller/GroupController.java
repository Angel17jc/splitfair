package com.expensesplit.controller;

import com.expensesplit.dto.request.ChangeRoleRequest;
import com.expensesplit.dto.request.CreateGroupRequest;
import com.expensesplit.dto.request.UpdateGroupRequest;
import com.expensesplit.dto.response.GroupResponse;
import com.expensesplit.dto.response.GroupSummaryResponse;
import com.expensesplit.dto.response.PagedResponse;
import com.expensesplit.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    /**
     * Tope del tamano de pagina. Un parametro sin limite permitiria pedir
     * cien mil grupos en una peticion y convertir el endpoint en una via de
     * denegacion de servicio.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final GroupService groupService;

    /**
     * Grupos a los que pertenece el usuario autenticado.
     *
     * <p>No hay parametro de orden: se devuelven del mas reciente al mas
     * antiguo. Permitir ordenar por un campo arbitrario abriria la puerta a
     * ordenaciones sin indice sobre tablas que crecen.
     */
    @GetMapping
    public PagedResponse<GroupSummaryResponse> listMyGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.clamp(size, 1, MAX_PAGE_SIZE));

        return groupService.listMyGroups(authentication.getName(), pageable);
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(groupService.createGroup(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long id,
                                                    Authentication authentication) {
        return ResponseEntity.ok(groupService.getGroup(id, authentication.getName()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateGroupRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(
                groupService.updateGroup(id, request, authentication.getName()));
    }

    /**
     * Promueve a administrador o devuelve a miembro corriente. Solo un
     * administrador puede hacerlo, y nunca hasta dejar al grupo sin ninguno.
     */
    @PatchMapping("/{id}/members/{userId}/role")
    public ResponseEntity<GroupResponse> changeMemberRole(@PathVariable Long id,
                                                            @PathVariable Long userId,
                                                            @Valid @RequestBody ChangeRoleRequest request,
                                                            Authentication authentication) {
        return ResponseEntity.ok(groupService.changeMemberRole(
                id, userId, request.getRole(), authentication.getName()));
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<GroupResponse> addMember(@PathVariable Long id,
                                                     @PathVariable Long userId,
                                                     Authentication authentication) {
        return ResponseEntity.ok(groupService.addMember(id, userId, authentication.getName()));
    }
}
