package com.expensesplit.controller;

import com.expensesplit.dto.request.CreateInvitationRequest;
import com.expensesplit.dto.response.InvitationPreviewResponse;
import com.expensesplit.dto.response.InvitationResponse;
import com.expensesplit.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    /**
     * Genera un link de invitacion. Solo un administrador del grupo.
     *
     * <p>El token en claro se devuelve una unica vez: en la base queda solo
     * su hash y no hay forma de recuperarlo. Si se pierde, se genera otro.
     */
    @PostMapping("/api/groups/{groupId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(@PathVariable Long groupId,
                                       @Valid @RequestBody(required = false) CreateInvitationRequest request,
                                       Authentication authentication) {
        return invitationService.create(groupId,
                request != null ? request : new CreateInvitationRequest(),
                authentication.getName());
    }

    /**
     * Vista previa de la invitacion, <b>publica</b>: quien abre el link aun
     * no tiene cuenta, asi que no puede haber autenticacion por delante.
     *
     * <p>Por eso expone lo minimo imprescindible para decidir si aceptar:
     * nombre del grupo y de quien invita. Ningun dato economico.
     */
    @GetMapping("/api/invitations/{token}")
    public InvitationPreviewResponse preview(@PathVariable String token) {
        return invitationService.preview(token);
    }
}
