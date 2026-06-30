package com.schooldays.controller;

import com.schooldays.service.notification.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GmailOAuthController {

    private final NotificationService notificationService;

    public GmailOAuthController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/api/oauth/google/gmail/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String gmailCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state
    ) {
        String returnUrl = notificationService.completeGmailConnection(code, state);
        return """
                <!doctype html>
                <html lang="en">
                  <head><meta charset="utf-8"><title>Gmail connected</title></head>
                  <body>
                    <p>Gmail connected. Returning to SchoolDays...</p>
                    <p><a id="return-link" href="%s">Return to SchoolDays</a></p>
                    <script>
                      const returnUrl = "%s";
                      if (window.opener) {
                        window.opener.postMessage({ type: "schooldays:gmail-connected" }, "*");
                        window.close();
                      } else if (returnUrl) {
                        window.location.replace(returnUrl);
                      }
                    </script>
                  </body>
                </html>
                """.formatted(escapeHtml(returnUrl), escapeJavaScript(returnUrl));
    }

    private String escapeHtml(String value) {
        return value == null || value.isBlank()
                ? "https://www.schooldays.cc"
                : value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeJavaScript(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "");
    }
}
