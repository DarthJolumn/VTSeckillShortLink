package com.jolumn.livemalluser.service;

import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemalluser.dto.RegisterRequest;
import com.jolumn.livemalluser.entity.User;
import com.jolumn.livemalluser.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void preRegister_returnsBcryptHash() {
        String hash = userService.preRegister("Test1234");
        assertThat(hash).startsWith("$2a$10$");
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("Test1234");
        String hashedPwd = "$2a$10$xxxx";

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(new User());

        assertThatCode(() -> userService.register(request, hashedPwd))
                .doesNotThrowAnyException();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_usernameExists_throws1012() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        String hashedPwd = "$2a$10$xxxx";

        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request, hashedPwd))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(1012);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_concurrentInsert_throws1012() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        String hashedPwd = "$2a$10$xxxx";

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> userService.register(request, hashedPwd))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(1012);
    }

    @Test
    void register_usernameTrimmed() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("  testuser  ");
        String hashedPwd = "$2a$10$xxxx";

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(new User());

        userService.register(request, hashedPwd);
        verify(userRepository).existsByUsername("testuser");
    }
}
