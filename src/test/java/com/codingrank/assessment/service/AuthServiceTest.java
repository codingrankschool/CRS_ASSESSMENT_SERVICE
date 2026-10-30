package com.codingrank.assessment.service;

import com.codingrank.assessment.dto.AuthResponse;
import com.codingrank.assessment.dto.LoginRequest;
import com.codingrank.assessment.model.User;
import com.codingrank.assessment.repository.UserRepository;
import com.codingrank.assessment.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void shouldLoginSuccessfullyWithValidCredentials() {
        String email = "manishparihar1441@gmail.com";
        String rawPassword = "password123";
        String encodedHash = "$2b$10$ZgraHHbKMslCCZ4gOyTzjeyInXiYF6/UZqpF581dzAAPg5Lh20HLy";

        User user = User.builder()
                .id("6a409c1ac739179b4607c22d")
                .email(email)
                .password(encodedHash)
                .name("Manish Parihar")
                .role("STUDENT")
                .accountStatus("ACTIVE")
                .verificationStatus("APPROVED")
                .build();

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(rawPassword)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, encodedHash)).thenReturn(true);
        when(jwtTokenProvider.generateToken(user.getId(), email, "Manish Parihar", "STUDENT"))
                .thenReturn("mocked.jwt.token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(604800000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(user.getId(), response.getUser().getId());
        assertEquals(email, response.getUser().getEmail());
        assertEquals("Manish Parihar", response.getUser().getName());
        assertEquals("STUDENT", response.getUser().getRole());
    }

    @Test
    void shouldThrowBadCredentialsWhenPasswordMismatch() {
        String email = "user@example.com";
        String rawPassword = "wrongPassword";
        String encodedHash = "$2b$10$dummyHash";

        User user = User.builder()
                .id("user_1")
                .email(email)
                .password(encodedHash)
                .build();

        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(rawPassword)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, encodedHash)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void shouldThrowBadCredentialsWhenUserNotFound() {
        LoginRequest request = LoginRequest.builder()
                .email("unknown@example.com")
                .password("anyPassword")
                .build();

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
