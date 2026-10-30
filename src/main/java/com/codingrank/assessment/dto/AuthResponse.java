package com.codingrank.assessment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresInMs;
    private UserSummaryDto user;
}
