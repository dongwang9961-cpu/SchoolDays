package com.schooldays.dto.enrollment;

import java.util.List;

public record MessageListResponse(List<MessageResponse> messages) {
    public MessageListResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
