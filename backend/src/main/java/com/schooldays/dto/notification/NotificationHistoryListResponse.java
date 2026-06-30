package com.schooldays.dto.notification;

import java.util.List;

public record NotificationHistoryListResponse(List<NotificationHistoryResponse> history) {
}
