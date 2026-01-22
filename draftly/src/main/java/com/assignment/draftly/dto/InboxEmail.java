package com.assignment.draftly.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class InboxEmail {
    String messageId;
    String threadId;
    String from;
    String subject;
    String body;
}
