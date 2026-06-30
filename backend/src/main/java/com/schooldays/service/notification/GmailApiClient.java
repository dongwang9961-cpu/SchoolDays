package com.schooldays.service.notification;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class GmailApiClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUri;
    private final String sendUri;
    private final String userInfoUri;

    public GmailApiClient(
            @Value("${schooldays.gmail.client-id:}") String clientId,
            @Value("${schooldays.gmail.client-secret:}") String clientSecret,
            @Value("${schooldays.gmail.redirect-uri:http://localhost:8080/api/oauth/google/gmail/callback}") String redirectUri,
            @Value("${schooldays.gmail.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
            @Value("${schooldays.gmail.send-uri:https://gmail.googleapis.com/gmail/v1/users/me/messages/send}") String sendUri,
            @Value("${schooldays.gmail.user-info-uri:https://openidconnect.googleapis.com/v1/userinfo}") String userInfoUri
    ) {
        this.restClient = RestClient.create();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenUri = tokenUri;
        this.sendUri = sendUri;
        this.userInfoUri = userInfoUri;
    }

    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public String authorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id=" + Urls.encode(clientId)
                + "&redirect_uri=" + Urls.encode(redirectUri)
                + "&scope=" + Urls.encode("openid email profile https://www.googleapis.com/auth/gmail.send")
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + Urls.encode(state);
    }

    public Map<String, Object> exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        return postForm(tokenUri, body);
    }

    public Map<String, Object> refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        return postForm(tokenUri, body);
    }

    public Map<String, Object> userInfo(String accessToken) {
        return restClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public Map<String, Object> sendRawMessage(String accessToken, String rawMessage) {
        return restClient.post()
                .uri(sendUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("raw", rawMessage))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> postForm(String uri, MultiValueMap<String, String> body) {
        return restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
