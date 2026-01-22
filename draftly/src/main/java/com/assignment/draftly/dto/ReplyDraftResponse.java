package com.assignment.draftly.dto;

import com.assignment.draftly.enums.DraftStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplyDraftResponse {

    private DraftStatus status;
    private String message;
    private String draftId;
    private String threadId;
    private String replyMessage;

    public static ReplyDraftResponse noReply(String threadId) {
        ReplyDraftResponse response = new ReplyDraftResponse(
                DraftStatus.NO_REPLY_REQUIRED,
                "This email does not require a reply",
                null,
                threadId,
                null
        );
        return response;
    }

    public static ReplyDraftResponse success(String draftId, String threadId, String replyMessage) {
        return new ReplyDraftResponse(
                DraftStatus.DRAFT_CREATED,
                "Reply draft created successfully",
                draftId,
                threadId,
                replyMessage
        );
    }

    public static ReplyDraftResponse failed(String message, String threadId) {
        return new ReplyDraftResponse(
                DraftStatus.FAILED,
                message,
                null,
                threadId,
                null
        );
    }
}

