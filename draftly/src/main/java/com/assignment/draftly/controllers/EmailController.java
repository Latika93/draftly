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
import com.assignment.draftly.dto.DraftEmailRequest;
import com.assignment.draftly.dto.EmailBodyResponse;
import com.assignment.draftly.dto.InboxEmail;
import com.assignment.draftly.dto.RejectReplyResponse;
import com.assignment.draftly.dto.ReplyDraftRequest;
import com.assignment.draftly.dto.ReplyDraftResponse;
import com.assignment.draftly.enums.Tone;
import com.assignment.draftly.integrations.GmailClient;
import com.assignment.draftly.services.AuthService;
import com.assignment.draftly.services.EmailDraftService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmailController {

    private final AuthService authService;
    private final GmailClient gmailClient;
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
            @RequestBody DraftEmailRequest request
    ) {
        return emailDraftService.generateDraft(auth, request.getRecipient(), request.getContext());
    }

    @PostMapping("/emails/draft/reply")
    public ResponseEntity<ReplyDraftResponse> draftReply(
            Authentication auth,
            @RequestBody ReplyDraftRequest request
    ) {
        log.info("[API_REQUEST] endpoint=/emails/draft/reply threadId={}", request.getThreadId());
        try {
            ReplyDraftResponse response = emailDraftService.generateReplyDraft(auth, request);
            log.info("[API_RESPONSE] endpoint=/emails/draft/reply threadId={} status={}", 
                    request.getThreadId(), response.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API_ERROR] endpoint=/emails/draft/reply threadId={} error={}", 
                    request.getThreadId(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/emails/draft/reply/regenerate")
    public ResponseEntity<ReplyDraftResponse> regenerateReplyDraft(
            Authentication auth,
            @RequestParam String threadId,
            @RequestBody(required = false) ReplyDraftRequest request
    ) {
        log.info("[API_REQUEST] endpoint=/emails/draft/reply/regenerate threadId={}", threadId);
        try {
            Tone tone = request != null ? request.getTone() : null;
            
            ReplyDraftResponse response = emailDraftService.regenerateReplyDraft(auth, threadId, tone);
            
            if (response == null) {
                log.error("[API_ERROR] endpoint=/emails/draft/reply/regenerate threadId={} error=Service returned null response", threadId);
                response = ReplyDraftResponse.failed("Service returned null response", threadId);
            }
            
            log.info("[API_RESPONSE] endpoint=/emails/draft/reply/regenerate threadId={} status={} draftId={}", 
                    threadId, response.getStatus(), response.getDraftId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API_ERROR] endpoint=/emails/draft/reply/regenerate threadId={} error={}", 
                    threadId, e.getMessage(), e);
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
        log.info("[API_REQUEST] endpoint=/emails/draft/reply/approve threadId={}", threadId);
        try {
            ApproveReplyResponse response = emailDraftService.approveReplyDraft(
                    auth,
                    threadId,
                    request.getReplyMessage()
            );

            log.info("[API_RESPONSE] endpoint=/emails/draft/reply/approve threadId={} status={} statusCode={}", 
                    threadId, response.getStatus(), response.getStatusCode());

            // Return response with appropriate HTTP status code
            if ("SUCCESS".equals(response.getStatus())) {
                return ResponseEntity.status(response.getStatusCode()).body(response);
            } else if (response.getStatusCode() == 400) {
                return ResponseEntity.badRequest().body(response);
            } else {
                return ResponseEntity.status(response.getStatusCode()).body(response);
            }
        } catch (Exception e) {
            log.error("[API_ERROR] endpoint=/emails/draft/reply/approve threadId={} error={}", 
                    threadId, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/emails/draft/reply/reject")
    public ResponseEntity<RejectReplyResponse> rejectReplyDraft(
            Authentication auth,
            @RequestParam String threadId
    ) {
        log.info("[API_REQUEST] endpoint=/emails/draft/reply/reject threadId={}", threadId);
        try {
            RejectReplyResponse response = emailDraftService.rejectReplyDraft(auth, threadId);

            log.info("[API_RESPONSE] endpoint=/emails/draft/reply/reject threadId={} status={} statusCode={}", 
                    threadId, response.getStatus(), response.getStatusCode());

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
        } catch (Exception e) {
            log.error("[API_ERROR] endpoint=/emails/draft/reply/reject threadId={} error={}", 
                    threadId, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/emails/thread/reject")
    public ResponseEntity<RejectReplyResponse> rejectThread(
            Authentication auth,
            @RequestParam String threadId
    ) {
        log.info("[API_REQUEST] endpoint=/emails/thread/reject threadId={}", threadId);
        try {
            RejectReplyResponse response = emailDraftService.rejectReplyDraft(auth, threadId);

            log.info("[API_RESPONSE] endpoint=/emails/thread/reject threadId={} status={} statusCode={}", 
                    threadId, response.getStatus(), response.getStatusCode());

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
        } catch (Exception e) {
            log.error("[API_ERROR] endpoint=/emails/thread/reject threadId={} error={}", 
                    threadId, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/emails/thread/body")
    public ResponseEntity<EmailBodyResponse> getEmailBody(
            Authentication auth,
            @RequestParam String threadId
    ) {
        log.info("[API_REQUEST] endpoint=/emails/thread/body threadId={}", threadId);
        try {
            EmailBodyResponse response = emailDraftService.getEmailBodyByThreadId(auth, threadId);
            log.info("[API_RESPONSE] endpoint=/emails/thread/body threadId={} status=SUCCESS", threadId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                log.warn("[API_RESPONSE] endpoint=/emails/thread/body threadId={} status=NOT_FOUND", threadId);
                return ResponseEntity.notFound().build();
            }
            log.error("[API_ERROR] endpoint=/emails/thread/body threadId={} error={}", threadId, e.getMessage(), e);
            throw e;
        }
    }

}