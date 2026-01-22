package com.assignment.draftly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailBodyResponse {
    private String threadId;
    private String from;
    private String subject;
    private String body;
    private String messageId;
}

