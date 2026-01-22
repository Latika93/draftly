package com.assignment.draftly.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import com.assignment.draftly.dto.LoginDto;
import com.assignment.draftly.dto.LoginResponseDto;
import com.assignment.draftly.entity.User;
import com.assignment.draftly.repository.UserRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final OAuth2AuthorizedClientService clientService;

    public LoginResponseDto login(LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword())
        );

        User user = (User) authentication.getPrincipal();
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new LoginResponseDto(user.getId(), accessToken, refreshToken);
    }

    public LoginResponseDto refreshToken(String refreshToken) {
        Long userId = jwtService.getUserIdFromToken(refreshToken);
        User user = userService.getUserById(userId);

        String accessToken = jwtService.generateAccessToken(user);

        return new LoginResponseDto(user.getId(), accessToken, refreshToken);
    }

    public String getAccessToken(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("Authentication cannot be null");
        }
        
        // If it's an OAuth2AuthenticationToken (from OAuth login), get token from OAuth2 client service
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
            OAuth2AuthorizedClient client =
                    clientService.loadAuthorizedClient(
                            oauth.getAuthorizedClientRegistrationId(),
                            oauth.getName()
                    );
            
            if (client != null && client.getAccessToken() != null) {
                String googleAccessToken = client.getAccessToken().getTokenValue();
                
                // Get user from OAuth2 principal
                Object oauthPrincipal = oauth.getPrincipal();
                if (oauthPrincipal instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2User) {
                    String email = oauth2User.getAttribute("email");
                    User oauthUser = userService.findOrCreateOAuthUser(email);
                    oauthUser.setOauthtoken(googleAccessToken);
                    userRepository.save(oauthUser);
                    
                    return googleAccessToken;
                }
            }
            throw new IllegalStateException("Could not retrieve OAuth2 access token");
        }
        
        // If it's a regular Authentication (from JWT login), get stored token from database
        Object principal = authentication.getPrincipal();
        if (principal == null || !(principal instanceof User user)) {
            throw new IllegalStateException("Invalid authentication principal");
        }
        
        String googleAccessToken = user.getOauthtoken();
        
        if (googleAccessToken == null) {
            throw new IllegalStateException("User has not connected Google account. Please log in via OAuth first.");
        }
        
        return googleAccessToken;
    }


}
