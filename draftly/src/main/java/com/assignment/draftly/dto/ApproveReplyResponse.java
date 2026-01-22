package com.assignment.draftly.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveReplyResponse {
    private String status;
    private String message;
    private String threadId;
    private int statusCode;
}


