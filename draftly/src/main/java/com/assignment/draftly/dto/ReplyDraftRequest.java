package com.assignment.draftly.dto;

import com.assignment.draftly.enums.Tone;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ReplyDraftRequest {
    private String threadId;
    private String messageId;
    private String from;
    private String subject;
    private String body;
    private Tone tone;
}
