package com.codingrank.assessment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryDto {
    private String id;
    private String email;
    private String name;
    private String role;
    private String accountStatus;
    private String verificationStatus;
    private String phoneNumber;
    private String avatar;
}
