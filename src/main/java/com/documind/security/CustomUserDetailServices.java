package com.documind.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.documind.entity.User;
import com.documind.repository.UserRepository;
import java.util.List;

public class CustomUserDetailServices implements UserDetailsService{
    
    private final UserRepository userRepository;

    public CustomUserDetailServices(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword() == null ? "" : user.getPassword(), // Google users have no password
            List.of() //no roles for now
        );
        
    }
}
