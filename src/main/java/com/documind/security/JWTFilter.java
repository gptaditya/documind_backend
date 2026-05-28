package com.documind.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.documind.utils.JWTUtil;
import java.util.List;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JWTFilter extends OncePerRequestFilter{
    private final JWTUtil jwtUtil;
    
    public JWTFilter(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain chain) 
                                  throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        // Only act if header exists and starts with "Bearer "
        if (header != null && header.startsWith("Bearer ")) {
            
            String token = header.substring(7); // strip "Bearer " prefix to get raw token
            
            if (jwtUtil.isValid(token)) {
                String email = jwtUtil.extractEmail(token);

                // This object represents an authenticated user in Spring Security
                // 3 args: principal (who), credentials (password — null for JWT), authorities (roles)
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(email, null, List.of());

                // Put this into the security context so Spring knows the request is authenticated
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            // If token is invalid, we just don't set auth — Spring will send 401
        }

        // Always continue the filter chain — don't block here, let Spring handle 401
        chain.doFilter(request, response);
    }
}
