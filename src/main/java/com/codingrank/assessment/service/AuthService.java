package com.codingrank.assessment.service;

import com.codingrank.assessment.dto.AuthResponse;
import com.codingrank.assessment.dto.LoginRequest;
import com.codingrank.assessment.dto.UserSummaryDto;
import com.codingrank.assessment.model.User;
import com.codingrank.assessment.repository.UserRepository;
import com.codingrank.assessment.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse login(LoginRequest request) {
        log.info("Authenticating user with email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Invalid password attempt for email: {}", request.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }

        if ("DISABLED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new IllegalArgumentException("Account is disabled. Please contact administration.");
        }

        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );

        UserSummaryDto userSummary = UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .verificationStatus(user.getVerificationStatus())
                .phoneNumber(user.getPhoneNumber())
                .avatar(user.getAvatar())
                .build();

        log.info("User {} authenticated successfully (role: {})", user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(jwtTokenProvider.getExpirationMs())
                .user(userSummary)
                .build();
    }

    public UserSummaryDto getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        return UserSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .verificationStatus(user.getVerificationStatus())
                .phoneNumber(user.getPhoneNumber())
                .avatar(user.getAvatar())
                .build();
    }
}
