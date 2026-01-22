package com.assignment.draftly.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DebugController {

    private final OAuth2AuthorizedClientService clientService;

    @GetMapping("/debug/token")
    public Object debug(Authentication authentication) {

        OAuth2AuthenticationToken oauth =
                (OAuth2AuthenticationToken) authentication;

        OAuth2AuthorizedClient client =
                clientService.loadAuthorizedClient(
                        oauth.getAuthorizedClientRegistrationId(),
                        oauth.getName()
                );

        return Map.of(
                "scopes", client.getAccessToken().getScopes(),
                "expiresAt", client.getAccessToken().getExpiresAt()
        );
    }
}
