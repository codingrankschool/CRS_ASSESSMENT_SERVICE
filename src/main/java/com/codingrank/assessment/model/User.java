package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "User")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String name;

    @Builder.Default
    private String role = "STUDENT";

    @Builder.Default
    private String verificationStatus = "APPROVED";

    @Builder.Default
    private String accountStatus = "ACTIVE";

    private String phoneNumber;
    private String avatar;
    private String bio;

    @Builder.Default
    private String provider = "local";

    private String providerId;
    private String resetToken;
    private Instant resetTokenExpiry;
    private String refreshToken;
    private Instant refreshTokenExpiry;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
