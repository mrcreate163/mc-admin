package com.socialnetwork.adminbot.client;

import com.socialnetwork.adminbot.exception.GatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthClient {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public boolean validateToken(String token) {
        try {
            Boolean isValid = restTemplate.getForObject(
                    gatewayUrl + "/auth/validate?token=" + token,
                    Boolean.class
            );
            return Boolean.TRUE.equals(isValid);
        } catch (HttpServerErrorException e) {
            log.error("Gateway error during token validation: {}", e.getMessage(), e);
            throw new GatewayException("Gateway error: " + e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Gateway unavailable during token validation: {}", e.getMessage(), e);
            throw new GatewayException("Gateway unavailable");
        }
    }
}
