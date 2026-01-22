package com.assignment.draftly.integrations;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.assignment.draftly.dto.InboxEmail;
import com.assignment.draftly.exceptionHandler.GmailApiException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GmailClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public List<String> fetchLast10SentMessageIds(String accessToken) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url =
                "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=in:sent&maxResults=10";

        try {
            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Map<String, Object>> messages =
                    (List<Map<String, Object>>) response.getBody().get("messages");

            return messages.stream()
                    .map(m -> (String) m.get("id"))
                    .toList();
        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to fetch sent messages from Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }


    // fetch full message
    public Map<String, Object> fetchMessageById(
            String accessToken,
            String messageId
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url =
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + messageId;

        try {
            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to fetch message from Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    private String decode(String data) {
        return new String(Base64.getUrlDecoder().decode(data));
    }

    private String extractBody(Map<String, Object> message) {

        Map<String, Object> payload =
                (Map<String, Object>) message.get("payload");

        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) payload.get("parts");

        if (parts == null) return "";

        for (Map<String, Object> part : parts) {
            if ("text/plain".equals(part.get("mimeType"))) {
                Map<String, Object> body =
                        (Map<String, Object>) part.get("body");

                return decode((String) body.get("data"));
            }
        }

        return "";
    }

    public List<String> fetchLast10SentEmailBodies(String accessToken) {

        List<String> ids = fetchLast10SentMessageIds(accessToken);
        List<String> bodies = new ArrayList<>();

        for (String id : ids) {
            Map<String, Object> message =
                    fetchMessageById(accessToken, id);

            String body = extractBody(message);
            bodies.add(body);
        }

        return bodies;
    }

    public void createDraft(
            String accessToken,
            String to,
            String subject,
            String body
    ) {

        String email =
                "To: " + to + "\r\n" +
                        "Subject: " + subject + "\r\n" +
                        "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                        "\r\n" +
                        body;

        String encodedEmail =
                Base64.getUrlEncoder()
                        .encodeToString(email.getBytes());

        Map<String, Object> message = Map.of(
                "raw", encodedEmail
        );

        Map<String, Object> draft = Map.of(
                "message", message
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(draft, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            "https://gmail.googleapis.com/gmail/v1/users/me/drafts",
                            entity,
                            Map.class
                    );

            log.info("Gmail draft created. Status: {}", response.getStatusCode());
            log.info("Response body: {}", response.getBody());

        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to create draft in Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }

    }

    public List<String> fetchLast50InboxMessageIds(String accessToken) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url =
                "https://gmail.googleapis.com/gmail/v1/users/me/messages" +
                        "?q=in:inbox&maxResults=10";

        try {
            log.info("Fetching inbox message IDs from Gmail API...");
            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            log.info("Gmail API response status: {}", response.getStatusCode());

            if (response.getBody() == null) {
                log.warn("Gmail API returned null response body");
                return List.of();
            }

            List<Map<String, Object>> messages =
                    (List<Map<String, Object>>) response.getBody().get("messages");

            if (messages == null) {
                log.info("No messages found in inbox");
                return List.of();
            }

            return messages.stream()
                    .map(m -> (String) m.get("id"))
                    .toList();
        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to fetch inbox messages from Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error fetching inbox message IDs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch inbox message IDs: " + e.getMessage(), e);
        }
    }

    private String getHeader(List<Map<String, Object>> headers, String name) {
        return headers.stream()
                .filter(h -> name.equalsIgnoreCase((String) h.get("name")))
                .map(h -> (String) h.get("value"))
                .findFirst()
                .orElse("");
    }

    public InboxEmail parseInboxEmail(Map<String, Object> message) {

        Map<String, Object> payload =
                (Map<String, Object>) message.get("payload");

        List<Map<String, Object>> headers =
                (List<Map<String, Object>>) payload.get("headers");

        InboxEmail email = new InboxEmail();
        email.setMessageId((String) message.get("id"));
        email.setThreadId((String) message.get("threadId"));

        email.setFrom(getHeader(headers, "From"));
        email.setSubject(getHeader(headers, "Subject"));
        email.setBody(extractBody(message));

        return email;
    }

    public List<InboxEmail> fetchLast50InboxEmails(String accessToken) {

        List<String> ids = fetchLast50InboxMessageIds(accessToken);
        List<InboxEmail> emails = new ArrayList<>();

        for (String id : ids) {
            Map<String, Object> message =
                    fetchMessageById(accessToken, id);

            emails.add(parseInboxEmail(message));
        }

        return emails;
    }

    public String createReplyDraft(
            String accessToken,
            String to,
            String subject,
            String body,
            String threadId,
            String messageId
    ) {
        String replySubject = subject.startsWith("Re:") ? subject : "Re: " + subject;

        String email =
                "To: " + to + "\r\n" +
                        "Subject: " + replySubject + "\r\n" +
                        "In-Reply-To: <" + messageId + "@mail.gmail.com>\r\n" +
                        "References: <" + messageId + "@mail.gmail.com>\r\n" +
                        "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                        "\r\n" +
                        body;

        String encodedEmail =
                Base64.getUrlEncoder()
                        .encodeToString(email.getBytes());

        Map<String, Object> message = new java.util.HashMap<>();
        message.put("raw", encodedEmail);
        message.put("threadId", threadId);

        Map<String, Object> draft = Map.of(
                "message", message
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(draft, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            "https://gmail.googleapis.com/gmail/v1/users/me/drafts",
                            entity,
                            Map.class
                    );

            log.info("Gmail reply draft created. Status: {}", response.getStatusCode());
            log.info("Response body: {}", response.getBody());

            if (response.getBody() != null) {
                Map<String, Object> draftResponse = (Map<String, Object>) response.getBody();
                return (String) draftResponse.get("id");
            }

            throw new RuntimeException("Gmail API returned null response body");

        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to create reply draft in Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public void sendReply(
            String accessToken,
            String to,
            String subject,
            String body,
            String threadId,
            String messageId
    ) {
        String replySubject = subject.startsWith("Re:") ? subject : "Re: " + subject;

        String email =
                "To: " + to + "\r\n" +
                        "Subject: " + replySubject + "\r\n" +
                        "In-Reply-To: <" + messageId + "@mail.gmail.com>\r\n" +
                        "References: <" + messageId + "@mail.gmail.com>\r\n" +
                        "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                        "\r\n" +
                        body;

        String encodedEmail =
                Base64.getUrlEncoder()
                        .encodeToString(email.getBytes());

        Map<String, Object> message = new java.util.HashMap<>();
        message.put("raw", encodedEmail);
        message.put("threadId", threadId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(message, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
                            entity,
                            Map.class
                    );

            log.info("Gmail reply sent. Status: {}", response.getStatusCode());
            log.info("Response body: {}", response.getBody());

        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to send reply in Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public Map<String, Object> fetchThreadById(String accessToken, String threadId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "https://gmail.googleapis.com/gmail/v1/users/me/threads/" + threadId;

        try {
            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to fetch thread from Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            } else if (e.getStatusCode().value() == 404) {
                errorMessage = "Thread not found with threadId: " + threadId;
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public String updateReplyDraft(
            String accessToken,
            String draftId,
            String to,
            String subject,
            String body,
            String threadId,
            String messageId
    ) {
        String replySubject = subject.startsWith("Re:") ? subject : "Re: " + subject;

        String email =
                "To: " + to + "\r\n" +
                        "Subject: " + replySubject + "\r\n" +
                        "In-Reply-To: <" + messageId + "@mail.gmail.com>\r\n" +
                        "References: <" + messageId + "@mail.gmail.com>\r\n" +
                        "Content-Type: text/plain; charset=\"UTF-8\"\r\n" +
                        "\r\n" +
                        body;

        String encodedEmail =
                Base64.getUrlEncoder()
                        .encodeToString(email.getBytes());

        Map<String, Object> message = new java.util.HashMap<>();
        message.put("raw", encodedEmail);
        message.put("threadId", threadId);

        Map<String, Object> draft = new java.util.HashMap<>();
        draft.put("id", draftId);
        draft.put("message", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(draft, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            "https://gmail.googleapis.com/gmail/v1/users/me/drafts/" + draftId,
                            HttpMethod.PUT,
                            entity,
                            Map.class
                    );

            log.info("Gmail reply draft updated. Status: {}", response.getStatusCode());
            log.info("Response body: {}", response.getBody());

            if (response.getBody() != null) {
                Map<String, Object> draftResponse = (Map<String, Object>) response.getBody();
                return (String) draftResponse.get("id");
            }

            return draftId;

        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            String errorMessage = "Failed to update reply draft in Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            } else if (e.getStatusCode().value() == 404) {
                // Draft might have been deleted, create a new one
                log.warn("Draft not found, creating new draft");
                return createReplyDraft(accessToken, to, subject, body, threadId, messageId);
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public void deleteDraft(String accessToken, String draftId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(
                    "https://gmail.googleapis.com/gmail/v1/users/me/drafts/" + draftId,
                    HttpMethod.DELETE,
                    entity,
                    Void.class
            );

            log.info("Gmail draft deleted successfully. Draft ID: {}", draftId);

        } catch (HttpClientErrorException e) {
            log.error("Gmail API error status: {}", e.getStatusCode());
            log.error("Gmail API error body: {}", e.getResponseBodyAsString());
            
            // If draft is already deleted (404), that's fine - just log it
            if (e.getStatusCode().value() == 404) {
                log.warn("Draft {} not found in Gmail (may have been already deleted)", draftId);
                return;
            }
            
            String errorMessage = "Failed to delete draft in Gmail API";
            if (e.getStatusCode().value() == 401) {
                errorMessage = "Gmail API authentication failed. Please reconnect your Google account.";
            } else if (e.getStatusCode().value() == 403) {
                errorMessage = "Gmail API access forbidden. Please check your permissions.";
            }
            throw new GmailApiException(errorMessage, e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

}
