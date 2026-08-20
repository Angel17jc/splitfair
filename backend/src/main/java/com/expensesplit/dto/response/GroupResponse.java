package com.expensesplit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private String createdByName;
    private LocalDateTime createdAt;
    private List<MemberResponse> members;

    @Data
    @Builder
    @AllArgsConstructor
    public static class MemberResponse {
        private Long userId;
        private String name;
        private String email;
        private String role;
    }
}
