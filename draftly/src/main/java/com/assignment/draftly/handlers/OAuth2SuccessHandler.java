package com.assignment.draftly.handlers;

import com.assignment.draftly.entity.User;
import com.assignment.draftly.services.JwtService;
import com.assignment.draftly.services.UserService;
import io.jsonwebtoken.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserService userService;
    private final OAuth2AuthorizedClientService clientService;

//    @Override
//    public void onAuthenticationSuccess(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            Authentication authentication
//    ) throws IOException, java.io.IOException {
//
//        OAuth2AuthenticationToken token =
//                (OAuth2AuthenticationToken) authentication;
//
//        DefaultOAuth2User oAuth2User =
//                (DefaultOAuth2User) token.getPrincipal();
//
//        String email = oAuth2User.getAttribute("email");
//
//        // 1️⃣ Save or fetch user
//        User user = userService.findOrCreateOAuthUser(email);
//
//        // 2️⃣ Store Google access token (INTERNAL USE ONLY)
//        OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
//                token.getAuthorizedClientRegistrationId(),
//                token.getName()
//        );
//
//        if (client != null && client.getAccessToken() != null) {
//            String googleAccessToken = client.getAccessToken().getTokenValue();
//            user.setOauthtoken(googleAccessToken);
//            userService.save(user);
//            log.info("Google access token stored for user: {}", email);
//        } else {
//            log.warn("Could not retrieve Google access token for user: {}", email);
//        }
//
//        log.info("OAuth login successful for: {}", email);
//
//        // 3️⃣ Generate YOUR JWT
//        String appJwt = jwtService.generateAccessToken(user);
//        log.info(appJwt);
//
//        // 4️⃣ Return JWT to frontend
//        response.setContentType("application/json");
//        response.getWriter().write("""
//        {
//          "token": "%s"
//        }
//        """.formatted(appJwt));
//    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, java.io.IOException {

        OAuth2AuthenticationToken token =
                (OAuth2AuthenticationToken) authentication;

        DefaultOAuth2User oAuth2User =
                (DefaultOAuth2User) token.getPrincipal();

        String email = oAuth2User.getAttribute("email");

        User user = userService.findOrCreateOAuthUser(email);

        OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName()
        );

        String googleAccessToken = null;
        if (client != null && client.getAccessToken() != null) {
            googleAccessToken = client.getAccessToken().getTokenValue();
            user.setOauthtoken(googleAccessToken);
            userService.save(user);
            log.info("Google access token stored for user: {}", email);
        } else {
            log.warn("Could not retrieve Google access token for user: {}", email);
        }

        String appJwt = jwtService.generateAccessToken(user);

        String redirectUrl = "http://localhost:5173/oauth-success?token=" + appJwt;
        if (googleAccessToken != null) {
            redirectUrl += "&oauthtoken=" + java.net.URLEncoder.encode(googleAccessToken, java.nio.charset.StandardCharsets.UTF_8);
        }

        clearAuthenticationAttributes(request);
        response.sendRedirect(redirectUrl);
    }

}
