package com.assignment.draftly.services;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.assignment.draftly.dto.EmailBodyResponse;
import com.assignment.draftly.dto.RejectReplyResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.assignment.draftly.dto.ApproveReplyResponse;
import com.assignment.draftly.dto.ReplyDraftRequest;
import com.assignment.draftly.dto.ReplyDraftResponse;
import com.assignment.draftly.entity.EmailReplyDraft;
import com.assignment.draftly.enums.DraftActionType;
import com.assignment.draftly.enums.ReplyDraftStatus;
import com.assignment.draftly.enums.Tone;
import com.assignment.draftly.exceptionHandler.GmailApiException;
import com.assignment.draftly.integrations.GmailClient;
import com.assignment.draftly.integrations.OpenAiClient;
import com.assignment.draftly.repository.EmailReplyDraftRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDraftService {

    private final OpenAiClient openAiClient;
    private final EmailService emailService; // your Gmail fetch service
    private final AuthService authService;
    private final GmailClient gmailClient;
    private final EmailReplyDraftRepository emailReplyDraftRepository;
    private final DraftLoggingService draftLoggingService;

    public String generateDraft(
            Authentication auth,
            String recipient,
            String context
    ) {

        List<String> sentEmails =
                emailService.getLast10SentEmailBodies(auth);

        String styleExamples = String.join(
                "\n\n---\n\n",
                sentEmails.subList(0, Math.min(5, sentEmails.size()))
        );

        String systemPrompt = """
You are an AI email writing assistant.
You must mimic the user's writing tone, structure, and style
based on the examples provided.
Do NOT copy content.
""";

        String userPrompt = """
Here are examples of my past sent emails:

%s

Now write a new email.

Recipient: %s
Context: %s
""".formatted(styleExamples, recipient, context);

//        return openAiClient.generate(systemPrompt, userPrompt);
        String aiDraft = openAiClient.generate(systemPrompt, userPrompt);

        String accessToken = authService.getAccessToken(auth);

        gmailClient.createDraft(
                accessToken,
                recipient,
                "Follow up",
                aiDraft
        );

        return aiDraft;
    }

    public ReplyDraftResponse generateReplyDraft(
            Authentication auth,
            ReplyDraftRequest request
    ) {
        String draftId = null;
        try {
            draftLoggingService.logAction(
                    DraftActionType.AI_GENERATION_STARTED,
                    null,
                    request.getThreadId(),
                    auth,
                    true,
                    "Starting AI reply generation"
            );

            String from = request.getFrom().toLowerCase();

            // 1. Hard no-reply guard (cheap + fast)
            if (from.contains("no-reply") || from.contains("noreply") || from.contains("do-not-reply")) {
                draftLoggingService.logAction(
                        DraftActionType.DRAFT_CREATED,
                        null,
                        request.getThreadId(),
                        auth,
                        false,
                        "No-reply email detected, skipping draft creation"
                );
                return ReplyDraftResponse.noReply(request.getThreadId());
            }

            // 2. Extract email address from "From" field
            String recipientEmail = extractEmailFromField(request.getFrom());

            // 3. Get user's writing style from past sent emails
            List<String> sentEmails = emailService.getLast10SentEmailBodies(auth);
            String styleExamples = String.join(
                    "\n\n---\n\n",
                    sentEmails.subList(0, Math.min(5, sentEmails.size()))
            );
            Tone tone = request.getTone();

            // 4. Generate AI reply based on subject and body with selected tone
            String toneInstruction = getToneInstruction(tone);
            
            String systemPrompt = """
                    You are an AI email writing assistant.
                    You must mimic the user's writing structure and style from the examples provided.
                    Maintain the same vocabulary patterns, sentence structure, and formatting style.
                    However, adjust the tone to be %s as requested.
                    Write a professional and appropriate reply to the email.
                    Do NOT copy content from the original email.
                    Keep the reply concise and relevant.
                    """.formatted(toneInstruction);

            String userPrompt = """
                    Here are examples of my past sent emails (use these to match writing style):
                    
                    %s
                    
                    Now write a reply to this email with a %s tone:
                    
                    From: %s
                    Subject: %s
                    Body: %s
                    """.formatted(styleExamples, toneInstruction.toLowerCase(), request.getFrom(), request.getSubject(), request.getBody());

            String aiReply = openAiClient.generate(systemPrompt, userPrompt);
            draftLoggingService.logAction(
                    DraftActionType.AI_GENERATION_COMPLETED,
                    null,
                    request.getThreadId(),
                    auth,
                    true,
                    "AI reply generation completed successfully"
            );

            // 5. Get access token and create Gmail reply draft
            String accessToken = authService.getAccessToken(auth);
            draftId = gmailClient.createReplyDraft(
                    accessToken,
                    recipientEmail,
                    request.getSubject(),
                    aiReply,
                    request.getThreadId(),
                    request.getMessageId()
            );

            // 6. Save reply draft to database
            EmailReplyDraft entity = new EmailReplyDraft();
            entity.setThreadId(request.getThreadId());
            entity.setMessageId(request.getMessageId());
            entity.setFromEmail(request.getFrom());
            entity.setToEmail(recipientEmail);
            entity.setReplyMessage(aiReply);
            entity.setStatus(ReplyDraftStatus.GENERATED);
            entity.setGmailDraftId(draftId);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            emailReplyDraftRepository.save(entity);
            
            draftLoggingService.logAction(
                    DraftActionType.DRAFT_CREATED,
                    draftId,
                    request.getThreadId(),
                    auth,
                    true,
                    "Reply draft created and saved to database"
            );

            // 7. Return success response with reply message
            return ReplyDraftResponse.success(draftId, request.getThreadId(), aiReply);

        } catch (Exception ex) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_CREATED,
                    draftId,
                    request.getThreadId(),
                    auth,
                    "Failed to generate reply draft: " + ex.getMessage(),
                    ex
            );

            return ReplyDraftResponse.failed(
                    "Unable to generate reply draft: " + ex.getMessage(),
                    request.getThreadId()
            );
        }
    }

    private String getToneInstruction(Tone tone) {
        switch (tone) {
            case FORMAL:
                return "formal and professional";
            case CONCISE:
                return "concise and direct";
            case FRIENDLY:
                return "friendly and warm";
            default:
                return "professional";
        }
    }

    private String extractEmailFromField(String fromField) {
        if (fromField == null || fromField.trim().isEmpty()) {
            throw new IllegalArgumentException("From field cannot be empty");
        }

        Pattern emailPattern = Pattern.compile("<([^>]+)>");
        Matcher matcher = emailPattern.matcher(fromField);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        Pattern simpleEmailPattern = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
        matcher = simpleEmailPattern.matcher(fromField);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return fromField.trim();
    }

    public ReplyDraftResponse regenerateReplyDraft(
            Authentication auth,
            String threadId,
            Tone tone
    ) {
        String draftId = null;
        try {
            draftLoggingService.logAction(
                    DraftActionType.DRAFT_EDITED,
                    null,
                    threadId,
                    auth,
                    true,
                    "Starting draft regeneration"
            );

            // 1. Find the most recent draft in database for this threadId
            List<EmailReplyDraft> drafts = emailReplyDraftRepository
                    .findByThreadIdAndDeletedFalseOrderByCreatedAtDesc(threadId);

            if (drafts.isEmpty()) {
                draftLoggingService.logAction(
                        DraftActionType.DRAFT_EDITED,
                        null,
                        threadId,
                        auth,
                        false,
                        "No reply draft found for regeneration"
                );
                return ReplyDraftResponse.failed(
                        "Reply draft not found for threadId: " + threadId,
                        threadId
                );
            }

            // 2. Get the most recent draft
            EmailReplyDraft draft = drafts.get(0);
            draftId = draft.getGmailDraftId();

            // 3. Get access token
            String accessToken = authService.getAccessToken(auth);

            // 4. Fetch original message to get subject and body
            Map<String, Object> originalMessage = gmailClient.fetchMessageById(accessToken, draft.getMessageId());
            String subject = extractSubjectFromMessage(originalMessage);
            String originalBody = extractBodyFromOriginalMessage(originalMessage);

            // 5. Use provided tone or default to FRIENDLY if not provided
            Tone selectedTone = tone != null ? tone : Tone.FRIENDLY;

            // 6. Get user's writing style from past sent emails
            List<String> sentEmails = emailService.getLast10SentEmailBodies(auth);
            String styleExamples = String.join(
                    "\n\n---\n\n",
                    sentEmails.subList(0, Math.min(5, sentEmails.size()))
            );

            // 7. Generate AI reply based on subject and body with selected tone
            String toneInstruction = getToneInstruction(selectedTone);
            
            String systemPrompt = """
                    You are an AI email writing assistant.
                    You must mimic the user's writing structure and style from the examples provided.
                    Maintain the same vocabulary patterns, sentence structure, and formatting style.
                    However, adjust the tone to be %s as requested.
                    Write a professional and appropriate reply to the email.
                    Do NOT copy content from the original email.
                    Keep the reply concise and relevant.
                    """.formatted(toneInstruction);

            String userPrompt = """
                    Here are examples of my past sent emails (use these to match writing style):
                    
                    %s
                    
                    Now write a reply to this email with a %s tone:
                    
                    From: %s
                    Subject: %s
                    Body: %s
                    """.formatted(styleExamples, toneInstruction.toLowerCase(), draft.getFromEmail(), subject, originalBody);

            String aiReply = openAiClient.generate(systemPrompt, userPrompt);
            draftLoggingService.logAction(
                    DraftActionType.AI_GENERATION_COMPLETED,
                    draftId,
                    threadId,
                    auth,
                    true,
                    "AI reply regeneration completed successfully"
            );

            // 8. Update or create Gmail draft
            String updatedDraftId;
            if (draft.getGmailDraftId() != null && !draft.getGmailDraftId().isEmpty()) {
                try {
                    updatedDraftId = gmailClient.updateReplyDraft(
                            accessToken,
                            draft.getGmailDraftId(),
                            draft.getToEmail(),
                            subject,
                            aiReply,
                            draft.getThreadId(),
                            draft.getMessageId()
                    );
                } catch (Exception e) {
                    draftLoggingService.logAction(
                            DraftActionType.GMAIL_DRAFT_UPDATED,
                            draft.getGmailDraftId(),
                            threadId,
                            auth,
                            false,
                            "Failed to update Gmail draft, creating new one: " + e.getMessage()
                    );
                    // If update fails, create a new draft
                    updatedDraftId = gmailClient.createReplyDraft(
                            accessToken,
                            draft.getToEmail(),
                            subject,
                            aiReply,
                            draft.getThreadId(),
                            draft.getMessageId()
                    );
                }
            } else {
                updatedDraftId = gmailClient.createReplyDraft(
                        accessToken,
                        draft.getToEmail(),
                        subject,
                        aiReply,
                        draft.getThreadId(),
                        draft.getMessageId()
                );
            }

            // 9. Update database record
            draft.setReplyMessage(aiReply);
            draft.setGmailDraftId(updatedDraftId);
            draft.setStatus(ReplyDraftStatus.GENERATED); // Keep as GENERATED since it's still a draft ready for approval
            draft.setUpdatedAt(Instant.now());

            emailReplyDraftRepository.save(draft);
            
            draftLoggingService.logAction(
                    DraftActionType.DRAFT_REGENERATED,
                    updatedDraftId,
                    threadId,
                    auth,
                    true,
                    "Reply draft regenerated and updated in database"
            );

            // 10. Return success response with reply message
            ReplyDraftResponse response = ReplyDraftResponse.success(updatedDraftId, threadId, aiReply);
            return response;

        } catch (Exception ex) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_REGENERATED,
                    draftId,
                    threadId,
                    auth,
                    "Failed to regenerate reply draft: " + ex.getMessage(),
                    ex
            );
            return ReplyDraftResponse.failed(
                    "Unable to regenerate reply draft: " + ex.getMessage(),
                    threadId
            );
        }
    }

    private String extractBodyFromOriginalMessage(Map<String, Object> message) {
        try {
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");
            if (payload == null) {
                return "";
            }

            // Check if message has parts (multipart)
            List<Map<String, Object>> parts = (List<Map<String, Object>>) payload.get("parts");
            if (parts != null && !parts.isEmpty()) {
                // Look for text/plain part
                for (Map<String, Object> part : parts) {
                    String mimeType = (String) part.get("mimeType");
                    if ("text/plain".equals(mimeType)) {
                        Map<String, Object> body = (Map<String, Object>) part.get("body");
                        if (body != null) {
                            String data = (String) body.get("data");
                            if (data != null) {
                                return new String(Base64.getUrlDecoder().decode(data));
                            }
                        }
                    }
                }
            } else {
                // Single part message
                String mimeType = (String) payload.get("mimeType");
                if ("text/plain".equals(mimeType)) {
                    Map<String, Object> body = (Map<String, Object>) payload.get("body");
                    if (body != null) {
                        String data = (String) body.get("data");
                        if (data != null) {
                            return new String(Base64.getUrlDecoder().decode(data));
                        }
                    }
                }
            }

            return "";
        } catch (Exception e) {
            log.warn("Failed to extract body from original message", e);
            return "";
        }
    }

    @Transactional
    public ApproveReplyResponse approveReplyDraft(Authentication auth, String threadId, String replyMessage) {
        String draftId = null;
        try {
            draftLoggingService.logAction(
                    DraftActionType.DRAFT_APPROVED,
                    null,
                    threadId,
                    auth,
                    true,
                    "Starting draft approval process"
            );

            // 1. Find the most recent GENERATED draft in database (ordered by createdAt DESC)
            List<EmailReplyDraft> drafts = emailReplyDraftRepository
                    .findByThreadIdAndDeletedFalseAndStatusOrderByCreatedAtDesc(threadId, ReplyDraftStatus.GENERATED);

            if (drafts.isEmpty()) {
                // Check if there are any drafts at all (in any status)
                List<EmailReplyDraft> allDrafts = emailReplyDraftRepository
                        .findByThreadIdAndDeletedFalseOrderByCreatedAtDesc(threadId);
                
                if (allDrafts.isEmpty()) {
                    draftLoggingService.logAction(
                            DraftActionType.DRAFT_APPROVED,
                            null,
                            threadId,
                            auth,
                            false,
                            "Reply draft not found for approval"
                    );
                    throw new RuntimeException("Reply draft not found for threadId: " + threadId);
                } else {
                    draftLoggingService.logAction(
                            DraftActionType.DRAFT_APPROVED,
                            allDrafts.get(0).getGmailDraftId(),
                            threadId,
                            auth,
                            false,
                            "Draft is not in GENERATED state. Current status: " + allDrafts.get(0).getStatus()
                    );
                    throw new IllegalStateException("Draft is not in GENERATED state. Current status: " + allDrafts.get(0).getStatus());
                }
            }

            // Get the most recent GENERATED draft
            EmailReplyDraft draft = drafts.get(0);
            draftId = draft.getGmailDraftId();

            // 2. Validate draft status (should already be GENERATED from query, but double-check)
            if (draft.getStatus() != ReplyDraftStatus.GENERATED) {
                throw new IllegalStateException("Draft is not in GENERATED state. Current status: " + draft.getStatus());
            }

            // 3. Validate reply message
            if (replyMessage == null || replyMessage.trim().isEmpty()) {
                throw new IllegalArgumentException("Reply message cannot be empty");
            }

            // 4. Get access token
            String accessToken = authService.getAccessToken(auth);

            // 5. Fetch original message to get subject
            Map<String, Object> originalMessage = gmailClient.fetchMessageById(accessToken, draft.getMessageId());
            String subject = extractSubjectFromMessage(originalMessage);

            // 6. Send the reply via Gmail API using the provided replyMessage with retry logic
            sendReplyWithRetry(
                    auth,
                    accessToken,
                    draft.getToEmail(),
                    subject,
                    replyMessage,
                    draft.getThreadId(),
                    draft.getMessageId(),
                    draftId
            );

            // 7. Update database status and save the new reply message
            draft.setStatus(ReplyDraftStatus.SENT);
            draft.setReplyMessage(replyMessage);
            draft.setUpdatedAt(Instant.now());
            emailReplyDraftRepository.save(draft);

            draftLoggingService.logAction(
                    DraftActionType.EMAIL_SENT,
                    draftId,
                    threadId,
                    auth,
                    true,
                    "Reply sent successfully to " + draft.getToEmail()
            );

            // 8. Return success response
            return new ApproveReplyResponse(
                    "SUCCESS",
                    "Reply sent successfully to " + draft.getToEmail(),
                    threadId,
                    200
            );

        } catch (IllegalArgumentException | IllegalStateException e) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_APPROVED,
                    draftId,
                    threadId,
                    auth,
                    "Validation error while approving reply draft: " + e.getMessage(),
                    e
            );
            return new ApproveReplyResponse(
                    "ERROR",
                    e.getMessage(),
                    threadId,
                    400
            );
        } catch (RuntimeException e) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_APPROVED,
                    draftId,
                    threadId,
                    auth,
                    "Error while approving reply draft: " + e.getMessage(),
                    e
            );
            return new ApproveReplyResponse(
                    "ERROR",
                    "Failed to send reply: " + e.getMessage(),
                    threadId,
                    500
            );
        } catch (Exception e) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_APPROVED,
                    draftId,
                    threadId,
                    auth,
                    "Unexpected error while approving reply draft: " + e.getMessage(),
                    e
            );
            return new ApproveReplyResponse(
                    "ERROR",
                    "An unexpected error occurred: " + e.getMessage(),
                    threadId,
                    500
            );
        }
    }

    private String extractSubjectFromMessage(Map<String, Object> message) {
        try {
            Map<String, Object> payload = (Map<String, Object>) message.get("payload");
            if (payload == null) {
                return "Re: Email";
            }

            List<Map<String, Object>> headers = (List<Map<String, Object>>) payload.get("headers");
            if (headers == null) {
                return "Re: Email";
            }

            return headers.stream()
                    .filter(h -> "Subject".equalsIgnoreCase((String) h.get("name")))
                    .map(h -> (String) h.get("value"))
                    .findFirst()
                    .orElse("Re: Email");
        } catch (Exception e) {
            log.warn("Failed to extract subject from message, using default", e);
            return "Re: Email";
        }
    }

    @Transactional
    public RejectReplyResponse rejectReplyDraft(Authentication auth, String threadId) {
        String draftId = null;
        try {
            draftLoggingService.logAction(
                    DraftActionType.DRAFT_REJECTED,
                    null,
                    threadId,
                    auth,
                    true,
                    "Starting draft rejection process"
            );

            // 1. Find the most recent draft in database (ordered by createdAt DESC)
            List<EmailReplyDraft> drafts = emailReplyDraftRepository
                    .findByThreadIdAndDeletedFalseAndStatusOrderByCreatedAtDesc(threadId, ReplyDraftStatus.GENERATED);

            if (drafts.isEmpty()) {
                // Check if there are any drafts at all (in any status)
                List<EmailReplyDraft> allDrafts = emailReplyDraftRepository
                        .findByThreadIdAndDeletedFalseOrderByCreatedAtDesc(threadId);
                
                if (allDrafts.isEmpty()) {
                    draftLoggingService.logAction(
                            DraftActionType.DRAFT_REJECTED,
                            null,
                            threadId,
                            auth,
                            false,
                            "Reply draft not found for rejection"
                    );
                    throw new RuntimeException("Reply draft not found for threadId: " + threadId);
                } else {
                    draftLoggingService.logAction(
                            DraftActionType.DRAFT_REJECTED,
                            allDrafts.get(0).getGmailDraftId(),
                            threadId,
                            auth,
                            false,
                            "No GENERATED drafts found. Current status: " + allDrafts.get(0).getStatus()
                    );
                    throw new IllegalStateException("No GENERATED drafts found for threadId: " + threadId + ". Current status: " + allDrafts.get(0).getStatus());
                }
            }

            // Get the most recent GENERATED draft
            EmailReplyDraft draft = drafts.get(0);
            draftId = draft.getGmailDraftId();

            // 2. Validate draft status (should already be GENERATED from query, but double-check)
            if (draft.getStatus() != ReplyDraftStatus.GENERATED) {
                throw new IllegalStateException("Only GENERATED drafts can be rejected. Current status: " + draft.getStatus());
            }

            // 3. Delete Gmail draft if it exists
            String gmailDraftId = draft.getGmailDraftId();
            if (gmailDraftId != null && !gmailDraftId.isEmpty()) {
                try {
                    String accessToken = authService.getAccessToken(auth);
                    gmailClient.deleteDraft(accessToken, gmailDraftId);
                } catch (Exception e) {
                    draftLoggingService.logAction(
                            DraftActionType.GMAIL_DRAFT_DELETED,
                            gmailDraftId,
                            threadId,
                            auth,
                            false,
                            "Failed to delete Gmail draft, continuing with database deletion: " + e.getMessage()
                    );
                    // Continue with database deletion even if Gmail deletion fails
                }
            }

            // 4. Actually delete the record from database
            emailReplyDraftRepository.delete(draft);
            
            draftLoggingService.logAction(
                    DraftActionType.DRAFT_REJECTED,
                    draftId,
                    threadId,
                    auth,
                    true,
                    "Reply draft rejected and deleted successfully"
            );

            // 5. Return success response
            return new RejectReplyResponse(
                    "SUCCESS",
                    "Reply draft rejected and deleted successfully",
                    threadId,
                    200
            );

        } catch (IllegalStateException e) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_REJECTED,
                    draftId,
                    threadId,
                    auth,
                    "Validation error while rejecting reply draft: " + e.getMessage(),
                    e
            );
            return new RejectReplyResponse(
                    "ERROR",
                    e.getMessage(),
                    threadId,
                    400
            );
        } catch (RuntimeException e) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_REJECTED,
                    draftId,
                    threadId,
                    auth,
                    "Error while rejecting reply draft: " + e.getMessage(),
                    e
            );
            return new RejectReplyResponse(
                    "ERROR",
                    "Failed to reject reply draft: " + e.getMessage(),
                    threadId,
                    404
            );
        } catch (Exception e) {
            draftLoggingService.logError(
                    DraftActionType.DRAFT_REJECTED,
                    draftId,
                    threadId,
                    auth,
                    "Unexpected error while rejecting reply draft: " + e.getMessage(),
                    e
            );
            return new RejectReplyResponse(
                    "ERROR",
                    "An unexpected error occurred: " + e.getMessage(),
                    threadId,
                    500
            );
        }
    }

    public EmailBodyResponse getEmailBodyByThreadId(
            Authentication auth,
            String threadId
    ) {
        try {
            // 1. Find the most recent draft in database for this threadId
            List<EmailReplyDraft> drafts = emailReplyDraftRepository
                    .findByThreadIdAndDeletedFalseOrderByCreatedAtDesc(threadId);

            if (drafts.isEmpty()) {
                log.warn("No reply draft found for threadId: {}", threadId);
                throw new RuntimeException("Reply draft not found for threadId: " + threadId);
            }

            // 2. Get the most recent draft
            EmailReplyDraft draft = drafts.get(0);

            // 3. Create and return response with data from database
            EmailBodyResponse response = new EmailBodyResponse();
            response.setThreadId(threadId);
            response.setMessageId(draft.getMessageId());
            response.setFrom(draft.getFromEmail());
            response.setSubject(""); // Subject not stored in database, can be empty or fetch from original message if needed
            response.setBody(draft.getReplyMessage());

            log.info("Successfully retrieved email body from database for threadId: {}", threadId);
            return response;

        } catch (RuntimeException e) {
            log.error("Error while fetching email body: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while fetching email body", e);
            throw new RuntimeException("Failed to fetch email body: " + e.getMessage(), e);
        }
    }

    /**
     * Sends email reply with automatic retry logic (3 attempts).
     * Only retries on transient errors (5xx, network errors, timeouts).
     * Does not retry on client errors (4xx) like authentication failures.
     */
    private void sendReplyWithRetry(
            Authentication auth,
            String accessToken,
            String to,
            String subject,
            String body,
            String threadId,
            String messageId,
            String draftId
    ) {
        final int MAX_RETRIES = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                gmailClient.sendReply(
                        accessToken,
                        to,
                        subject,
                        body,
                        threadId,
                        messageId
                );

                // Success - log if it was a retry
                if (attempt > 1) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("retryAttempt", attempt);
                    context.put("totalAttempts", attempt);
                    draftLoggingService.logAction(
                            DraftActionType.EMAIL_SENT,
                            draftId,
                            threadId,
                            auth,
                            true,
                            "Reply sent successfully after " + attempt + " attempt(s)",
                            context
                    );
                }
                return; // Success, exit retry loop

            } catch (GmailApiException e) {
                lastException = e;
                int statusCode = e.getStatusCode();

                // Don't retry on client errors (4xx) - these are permanent failures
                if (statusCode >= 400 && statusCode < 500) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("statusCode", statusCode);
                    context.put("attempt", attempt);
                    context.put("retryable", false);
                    draftLoggingService.logError(
                            DraftActionType.EMAIL_SENT,
                            draftId,
                            threadId,
                            auth,
                            "Failed to send email (non-retryable error): " + e.getMessage(),
                            e,
                            context
                    );
                    throw e; // Don't retry client errors
                }

                // Retry on server errors (5xx) and other exceptions
                Map<String, Object> context = new HashMap<>();
                context.put("statusCode", statusCode);
                context.put("attempt", attempt);
                context.put("maxRetries", MAX_RETRIES);
                context.put("retryable", true);

                if (attempt < MAX_RETRIES) {
                    long delayMs = calculateBackoffDelay(attempt);
                    draftLoggingService.logAction(
                            DraftActionType.EMAIL_SENT,
                            draftId,
                            threadId,
                            auth,
                            false,
                            "Failed to send email (attempt " + attempt + "/" + MAX_RETRIES + "), retrying in " + delayMs + "ms: " + e.getMessage(),
                            context
                    );
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    // Last attempt failed
                    draftLoggingService.logError(
                            DraftActionType.EMAIL_SENT,
                            draftId,
                            threadId,
                            auth,
                            "Failed to send email after " + MAX_RETRIES + " attempts: " + e.getMessage(),
                            e,
                            context
                    );
                }

            } catch (Exception e) {
                lastException = e;
                // Retry on other exceptions (network errors, timeouts, etc.)
                Map<String, Object> context = new HashMap<>();
                context.put("attempt", attempt);
                context.put("maxRetries", MAX_RETRIES);
                context.put("retryable", true);
                context.put("exceptionType", e.getClass().getName());

                if (attempt < MAX_RETRIES) {
                    long delayMs = calculateBackoffDelay(attempt);
                    draftLoggingService.logAction(
                            DraftActionType.EMAIL_SENT,
                            draftId,
                            threadId,
                            auth,
                            false,
                            "Failed to send email (attempt " + attempt + "/" + MAX_RETRIES + "), retrying in " + delayMs + "ms: " + e.getMessage(),
                            context
                    );
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    // Last attempt failed
                    draftLoggingService.logError(
                            DraftActionType.EMAIL_SENT,
                            draftId,
                            threadId,
                            auth,
                            "Failed to send email after " + MAX_RETRIES + " attempts: " + e.getMessage(),
                            e,
                            context
                    );
                }
            }
        }

        // All retries exhausted, throw the last exception
        if (lastException != null) {
            throw new RuntimeException("Failed to send email after " + MAX_RETRIES + " attempts", lastException);
        }
    }

    /**
     * Calculates exponential backoff delay in milliseconds.
     * Formula: baseDelay * (2 ^ (attempt - 1))
     * Attempt 1: 1 second
     * Attempt 2: 2 seconds
     * Attempt 3: 4 seconds
     */
    private long calculateBackoffDelay(int attempt) {
        long baseDelayMs = 1000; // 1 second base delay
        return baseDelayMs * (long) Math.pow(2, attempt - 1);
    }

}

