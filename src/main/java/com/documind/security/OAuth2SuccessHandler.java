package com.documind.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.documind.entity.User;
import com.documind.repository.UserRepository;
import com.documind.utils.JWTUtil;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JWTUtil jwtUtil;

    public OAuth2SuccessHandler(UserRepository userRepository, JWTUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // After Google login, Spring wraps the user's Google profile in OAuth2User
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        
        // Google provides "email" as an attribute in the profile
        String email = user.getAttribute("email");
        String name = user.getAttribute("name");
        User existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser == null) {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setUsername(email); // Google users have no username — use email as identifier
            newUser.setProvider("GOOGLE");
            userRepository.save(newUser);
        }
        // Generate a JWT that your frontend will use going forward
        String token = jwtUtil.generate(email);

        // Send the user back to the frontend with the token
        // Frontend reads ?token=xxx from the URL, stores it, and uses it for API calls
        String frontendUrl = "https://documind.xadisri.in";
        response.sendRedirect(frontendUrl + "/auth/callback?token=" + token);
    }
}
