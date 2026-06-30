package com.schooldays.service.notification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class Urls {

    private Urls() {
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
