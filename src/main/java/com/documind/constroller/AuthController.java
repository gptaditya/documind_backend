package com.documind.constroller;

import org.springframework.web.bind.annotation.RestController;

import com.documind.dto.LoginRequest;
import com.documind.dto.RegisterRequest;
import com.documind.entity.User;
import com.documind.repository.UserRepository;
import com.documind.utils.JWTUtil;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
 //allow 3000 port for frontend
@CrossOrigin("*")
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    // Constructor for dependency injection(autowiring)
    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JWTUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    //register
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // Check if email is already taken
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email already registered"));
        }

        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setProvider("LOCAL");
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Registration successful"));
    }


    //login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Check if email exists
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
         // User doesn't exist
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }
         User user = userOpt.get();
         // Google users have no password — they can't log in with credentials
        if (!user.getProvider().equalsIgnoreCase("LOCAL")) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Please login with Google"));
        }
        // BCrypt checks if the raw password matches the stored hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }
        String token = jwtUtil.generate(user.getEmail());
        return ResponseEntity.ok(Map.of("token", token));
    }

}
