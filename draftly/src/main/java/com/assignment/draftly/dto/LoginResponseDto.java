package com.assignment.draftly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class LoginResponseDto {
    private Long id;
    private String accessToken;
    private String refreshToken;
}
