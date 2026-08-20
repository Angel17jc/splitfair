package com.expensesplit.controller;

import com.expensesplit.dto.request.CreateGroupRequest;
import com.expensesplit.dto.response.GroupResponse;
import com.expensesplit.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

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

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<GroupResponse> addMember(@PathVariable Long id,
                                                     @PathVariable Long userId,
                                                     Authentication authentication) {
        return ResponseEntity.ok(groupService.addMember(id, userId, authentication.getName()));
    }
}
