package com.assignment.draftly.entity;

import com.assignment.draftly.enums.ReplyDraftStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "email_reply_drafts")
public class EmailReplyDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String threadId;
    private String messageId;

    private String fromEmail;
    private String toEmail;

    @Column(columnDefinition = "TEXT")
    private String replyMessage;

    @Enumerated(EnumType.STRING)
    private ReplyDraftStatus status;

    private String gmailDraftId;

    private Instant createdAt;
    private Instant updatedAt;

    private boolean deleted = false;
}
