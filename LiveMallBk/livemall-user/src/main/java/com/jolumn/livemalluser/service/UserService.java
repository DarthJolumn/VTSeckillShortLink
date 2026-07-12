package com.jolumn.livemalluser.service;

import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemalluser.dto.RegisterRequest;
import com.jolumn.livemalluser.entity.User;
import com.jolumn.livemalluser.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String preRegister(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    @Transactional
    public void register(RegisterRequest request, String hashedPwd) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BizException(1012, "用户名已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPwd);
        user.setPhone(request.getPhone());
        user.setRole(1);
        user.setStatus(1);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("并发注册冲突, username={}", username, e);
            throw new BizException(1012, "用户名已被注册");
        }
    }
}
