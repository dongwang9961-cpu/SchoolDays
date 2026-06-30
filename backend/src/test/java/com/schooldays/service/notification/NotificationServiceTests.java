package com.schooldays.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

class NotificationServiceTests {

    @Test
    void rawTemplateMessagePreservesMultipartRelatedTemplateAndOverridesDeliveryHeaders() throws Exception {
        NotificationService service = new NotificationService(null, null, null, null, List.of("http://localhost:5173"));
        Method method = NotificationService.class.getDeclaredMethod(
                "rawTemplateMessage",
                String.class,
                String.class,
                List.class,
                List.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        String template = """
                Delivered-To: old@example.com
                From: Original Sender <old@example.com>
                Subject: survival class
                To: old-recipient@example.com
                MIME-Version: 1.0
                Content-Type: multipart/related; boundary="related-boundary"

                --related-boundary
                Content-Type: multipart/alternative; boundary="alternative-boundary"

                --alternative-boundary
                Content-Type: text/plain; charset="UTF-8"

                [image: image.png]

                --alternative-boundary
                Content-Type: text/html; charset="UTF-8"

                <div dir="ltr"><img src="cid:ii_mqiwra7g0" alt="image.png"></div>

                --alternative-boundary--
                --related-boundary
                Content-Type: image/png; name="image.png"
                Content-Disposition: inline; filename="image.png"
                Content-Transfer-Encoding: base64
                Content-ID: <ii_mqiwra7g0>
                X-Attachment-Id: ii_mqiwra7g0

                iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ

                --related-boundary--
                """;

        String encoded = (String) method.invoke(
                service,
                "dongwang9961@gmail.com",
                "dong wang",
                List.of("cc@example.com"),
                List.of("bcc@example.com"),
                "Overridden subject",
                template
        );
        String message = new String(Base64.getUrlDecoder().decode(padBase64(encoded)), StandardCharsets.UTF_8);

        assertThat(message).contains("From: dong wang <dongwang9961@gmail.com>");
        assertThat(message).contains("To: dong wang <dongwang9961@gmail.com>");
        assertThat(message).contains("Cc: cc@example.com");
        assertThat(message).contains("Bcc: bcc@example.com");
        assertThat(message).contains("Subject: Overridden subject");
        assertThat(message).contains("Content-Type: multipart/related; boundary=\"related-boundary\"");
        assertThat(message).contains("--related-boundary");
        assertThat(message).contains("Content-Type: multipart/alternative; boundary=\"alternative-boundary\"");
        assertThat(message).contains("cid:ii_mqiwra7g0");
        assertThat(message).contains("Content-ID: <ii_mqiwra7g0>");
        assertThat(message).contains("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ");
        assertThat(message).doesNotContain("Original Sender <old@example.com>");
        assertThat(message).doesNotContain("Subject: survival class");
        assertThat(message).doesNotContain("To: old-recipient@example.com");
    }

    private String padBase64(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return value + "=".repeat(padding);
    }
}
