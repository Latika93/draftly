package com.assignment.draftly.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.assignment.draftly.dto.ApproveReplyRequest;
import com.assignment.draftly.dto.ApproveReplyResponse;
import com.assignment.draftly.dto.EmailBodyResponse;
import com.assignment.draftly.dto.InboxEmail;
import com.assignment.draftly.dto.RejectReplyResponse;
import com.assignment.draftly.dto.ReplyDraftRequest;
import com.assignment.draftly.dto.ReplyDraftResponse;
import com.assignment.draftly.enums.Tone;
import com.assignment.draftly.integrations.GmailClient;
import com.assignment.draftly.services.AuthService;
import com.assignment.draftly.services.EmailDraftService;
import com.assignment.draftly.services.EmailService;
import com.assignment.draftly.services.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmailController {

    private final AuthService authService;
    private final EmailService emailService;
    private final GmailClient gmailClient;
    private final JwtService jwtService;
    private final EmailDraftService emailDraftService;

    @GetMapping("/emails")
    public List<String> getEmails(Authentication authentication) {

        String accessToken =
                authService.getAccessToken(authentication);
        log.info(accessToken);
        return gmailClient.fetchLast10SentEmailBodies(accessToken);
    }

    @GetMapping("/emails/inbox")
    public List<InboxEmail> getInboxEmails(Authentication authentication) {

        String accessToken =
                authService.getAccessToken(authentication);

        return gmailClient.fetchLast50InboxEmails(accessToken);
    }


    @PostMapping("/emails/draft")
    public String draftEmail(
            Authentication auth,
            @RequestParam String recipient,
            @RequestParam String context
    ) {
        return emailDraftService.generateDraft(auth, recipient, context);
    }

    @PostMapping("/emails/draft/reply")
    public ResponseEntity<ReplyDraftResponse> draftReply(
            Authentication auth,
            @RequestBody ReplyDraftRequest request
    ) {
        return ResponseEntity.ok(
                emailDraftService.generateReplyDraft(auth, request)
        );
    }

    @PostMapping("/emails/draft/reply/regenerate")
    public ResponseEntity<ReplyDraftResponse> regenerateReplyDraft(
            Authentication auth,
            @RequestParam String threadId,
            @RequestBody(required = false) ReplyDraftRequest request
    ) {
        log.info("Regenerate reply draft request received for threadId: {}", threadId);
        try {
            Tone tone = request != null ? request.getTone() : null;
            log.info("Tone from request: {}", tone);
            
            ReplyDraftResponse response = emailDraftService.regenerateReplyDraft(auth, threadId, tone);
            
            if (response == null) {
                log.error("Service returned null response for threadId: {}", threadId);
                response = ReplyDraftResponse.failed("Service returned null response", threadId);
            }
            
            log.info("Regenerate reply draft response: status={}, draftId={}, threadId={}, hasReplyMessage={}", 
                    response.getStatus(), response.getDraftId(), response.getThreadId(), 
                    response.getReplyMessage() != null);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in regenerateReplyDraft controller for threadId: {}", threadId, e);
            ReplyDraftResponse errorResponse = ReplyDraftResponse.failed(
                    "Failed to regenerate reply draft: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    threadId
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }



    @PostMapping("/emails/draft/reply/approve")
    public ResponseEntity<ApproveReplyResponse> approveReplyDraft(
            Authentication auth,
            @RequestParam String threadId,
            @RequestBody ApproveReplyRequest request
    ) {
        ApproveReplyResponse response = emailDraftService.approveReplyDraft(
                auth,
                threadId,
                request.getReplyMessage()
        );

        // Return response with appropriate HTTP status code
        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.status(response.getStatusCode()).body(response);
        } else if (response.getStatusCode() == 400) {
            return ResponseEntity.badRequest().body(response);
        } else {
            return ResponseEntity.status(response.getStatusCode()).body(response);
        }
    }

    @PostMapping("/emails/draft/reply/reject")
    public ResponseEntity<RejectReplyResponse> rejectReplyDraft(
            Authentication auth,
            @RequestParam String threadId
    ) {
        RejectReplyResponse response = emailDraftService.rejectReplyDraft(auth, threadId);

        // Return response with appropriate HTTP status code
        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.status(response.getStatusCode()).body(response);
        } else if (response.getStatusCode() == 400) {
            return ResponseEntity.badRequest().body(response);
        } else if (response.getStatusCode() == 404) {
            return ResponseEntity.status(404).body(response);
        } else {
            return ResponseEntity.status(response.getStatusCode()).body(response);
        }
    }

    @GetMapping("/emails/thread/body")
    public ResponseEntity<EmailBodyResponse> getEmailBody(
            Authentication auth,
            @RequestParam String threadId
    ) {
        try {
            EmailBodyResponse response = emailDraftService.getEmailBodyByThreadId(auth, threadId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                log.warn("Reply draft not found for threadId: {}", threadId);
                return ResponseEntity.notFound().build();
            }
            log.error("Error fetching email body for threadId: {}", threadId, e);
            throw e;
        }
    }

}