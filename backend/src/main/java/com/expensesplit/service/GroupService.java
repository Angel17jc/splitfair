package com.expensesplit.service;

import com.expensesplit.dto.request.CreateGroupRequest;
import com.expensesplit.dto.response.GroupResponse;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.model.*;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String creatorEmail) {
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(creator)
                .build();

        groupRepository.save(group);

        GroupMember adminMember = GroupMember.builder()
                .group(group)
                .user(creator)
                .role(GroupRole.ADMIN)
                .build();

        groupMemberRepository.save(adminMember);

        return toResponse(group, List.of(adminMember));
    }

    @Transactional
    public GroupResponse addMember(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("El usuario ya pertenece al grupo");
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupRole.MEMBER)
                .build();

        groupMemberRepository.save(member);

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        return toResponse(group, members);
    }

    public GroupResponse getGroup(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        return toResponse(group, members);
    }

    private GroupResponse toResponse(Group group, List<GroupMember> members) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .createdByName(group.getCreatedBy().getName())
                .createdAt(group.getCreatedAt())
                .members(members.stream().map(m -> GroupResponse.MemberResponse.builder()
                        .userId(m.getUser().getId())
                        .name(m.getUser().getName())
                        .email(m.getUser().getEmail())
                        .role(m.getRole().name())
                        .build()).toList())
                .build();
    }
}
