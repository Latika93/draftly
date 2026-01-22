package com.assignment.draftly.services;

import com.assignment.draftly.entity.User;
import com.assignment.draftly.integrations.GmailClient;
import com.assignment.draftly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final GmailClient gmailClient;

    public List<String> getLast10SentEmailBodies(Authentication auth) {

        User user = (User) auth.getPrincipal();

        String googleAccessToken = user.getOauthtoken();
        if (googleAccessToken == null) {
            throw new IllegalStateException("User has not connected Google account");
        }

        log.info("Using Google token (partial): {}",
                googleAccessToken.substring(0, 20));

        return gmailClient.fetchLast10SentEmailBodies(googleAccessToken);
    }
}
