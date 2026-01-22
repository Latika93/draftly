package com.assignment.draftly.dto;

import lombok.Data;

@Data
public class DraftEmailRequest {
    private String recipient;
    private String context;
}
