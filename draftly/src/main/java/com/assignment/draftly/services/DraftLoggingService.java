package com.assignment.draftly.services;

import com.assignment.draftly.enums.DraftActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftLoggingService {

    private final UserService userService;

    public void logAction(
            DraftActionType actionType,
            String draftId,
            String threadId,
            Authentication auth,
            boolean success,
            String message,
            Map<String, Object> additionalContext
    ) {
        try {
            Long userId = extractUserId(auth);
            String userEmail = extractUserEmail(auth);

            Map<String, Object> logContext = new HashMap<>();
            logContext.put("timestamp", Instant.now().toString());
            logContext.put("actionType", actionType.name());
            logContext.put("draftId", draftId);
            logContext.put("threadId", threadId);
            logContext.put("userId", userId);
            logContext.put("userEmail", userEmail);
            logContext.put("outcome", success ? "SUCCESS" : "FAILURE");
            logContext.put("message", message);

            if (additionalContext != null) {
                logContext.putAll(additionalContext);
            }

            String logMessage = buildLogMessage(logContext);
            
            if (success) {
                log.info(logMessage);
            } else {
                log.error(logMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to log action: {}", e.getMessage());
        }
    }

    public void logAction(
            DraftActionType actionType,
            String draftId,
            String threadId,
            Authentication auth,
            boolean success,
            String message
    ) {
        logAction(actionType, draftId, threadId, auth, success, message, null);
    }

    public void logError(
            DraftActionType actionType,
            String draftId,
            String threadId,
            Authentication auth,
            String errorMessage,
            Throwable exception,
            Map<String, Object> additionalContext
    ) {
        try {
            Long userId = extractUserId(auth);
            String userEmail = extractUserEmail(auth);

            Map<String, Object> logContext = new HashMap<>();
            logContext.put("timestamp", Instant.now().toString());
            logContext.put("actionType", actionType.name());
            logContext.put("draftId", draftId);
            logContext.put("threadId", threadId);
            logContext.put("userId", userId);
            logContext.put("userEmail", userEmail);
            logContext.put("outcome", "FAILURE");
            logContext.put("errorMessage", errorMessage);
            logContext.put("exceptionType", exception != null ? exception.getClass().getName() : null);
            logContext.put("exceptionMessage", exception != null ? exception.getMessage() : null);

            if (additionalContext != null) {
                logContext.putAll(additionalContext);
            }

            String logMessage = buildLogMessage(logContext);
            log.error(logMessage, exception);
        } catch (Exception e) {
            log.warn("Failed to log error: {}", e.getMessage());
        }
    }

    public void logError(
            DraftActionType actionType,
            String draftId,
            String threadId,
            Authentication auth,
            String errorMessage,
            Throwable exception
    ) {
        logError(actionType, draftId, threadId, auth, errorMessage, exception, null);
    }

    public void logGmailApiOperation(
            DraftActionType actionType,
            String draftId,
            String threadId,
            Authentication auth,
            boolean success,
            String operation,
            Integer statusCode,
            String errorDetails
    ) {
        Map<String, Object> context = new HashMap<>();
        context.put("gmailOperation", operation);
        context.put("httpStatusCode", statusCode);
        if (errorDetails != null) {
            context.put("gmailErrorDetails", errorDetails);
        }

        logAction(
                actionType,
                draftId,
                threadId,
                auth,
                success,
                success ? "Gmail API operation completed: " + operation : "Gmail API operation failed: " + operation,
                context
        );
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }

        try {
            if (auth.getPrincipal() instanceof com.assignment.draftly.entity.User user) {
                return user.getId();
            } else if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2User) {
                String email = oauth2User.getAttribute("email");
                if (email != null) {
                    com.assignment.draftly.entity.User user = userService.findOrCreateOAuthUser(email);
                    return user.getId();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from authentication: {}", e.getMessage());
        }

        return null;
    }

    private String extractUserEmail(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }

        try {
            if (auth.getPrincipal() instanceof com.assignment.draftly.entity.User user) {
                return user.getEmail();
            } else if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2User) {
                return oauth2User.getAttribute("email");
            }
        } catch (Exception e) {
            log.debug("Could not extract user email from authentication: {}", e.getMessage());
        }

        return null;
    }

    private String buildLogMessage(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DRAFT_ACTION] ");
        
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getValue() != null) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }
        }
        
        return sb.toString().trim();
    }
}
