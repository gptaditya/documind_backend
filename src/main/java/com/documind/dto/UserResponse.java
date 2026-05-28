package com.documind.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String username;
    private String email;
    private String provider;
    private LocalDateTime createdAt;
}
