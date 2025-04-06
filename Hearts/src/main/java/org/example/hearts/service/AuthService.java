package org.example.hearts.service;

import lombok.RequiredArgsConstructor;
import org.example.hearts.exception.AuthException;
import org.example.hearts.model.dto.LoginRequest;
import org.example.hearts.model.dto.RegisterRequest;
import org.example.hearts.model.entity.Role;
import org.example.hearts.model.entity.User;
import org.example.hearts.repository.RoleRepository;
import org.example.hearts.repository.UserRepository;
import org.example.hearts.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;


@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * @param request RegisterRequest
     * @return User
     */
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setEnabled(true);

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new AuthException("Default role not found"));
        user.setRoles(Collections.singleton(userRole));

        return userRepository.save(user);
    }

    /**
     * @param request LoginRequest
     * @return JWT
     */
    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException("Invalid password");
        }

        return tokenProvider.generateToken(user);
    }
}
