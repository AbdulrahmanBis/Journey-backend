package com.journey.feature.auth.dto;

import com.journey.feature.user.dto.UserDto;

public record AuthResponse(UserDto user, String token) {}
