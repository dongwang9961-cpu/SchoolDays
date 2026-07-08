package com.schooldays.dto.auth;

import java.util.List;

public record InviteUserResponse(
        String status,
        List<InviteUserResultResponse> results
) {
}
