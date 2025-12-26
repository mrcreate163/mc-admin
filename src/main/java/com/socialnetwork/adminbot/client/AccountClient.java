package com.socialnetwork.adminbot.client;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.PageAccountDto;
import com.socialnetwork.adminbot.exception.GatewayException;
import com.socialnetwork.adminbot.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClient {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public AccountDto getAccountById(UUID userId) {
        try {
            ResponseEntity<AccountDto> response = restTemplate.getForEntity(
                    gatewayUrl + "/account/" + userId,
                    AccountDto.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new UserNotFoundException("User not found: " + userId);

        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException("User not found: " + userId);
        } catch (HttpServerErrorException e) {
            log.error("Gateway error: {}", e.getMessage(), e);
            throw new GatewayException("Gateway error: " + e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Gateway unavailable: {}", e.getMessage(), e);
            throw new GatewayException("Gateway unavailable");
        }
    }

    public void blockAccount(UUID userId) {
        try {
            restTemplate.put(gatewayUrl + "/account/block/" + userId, null);
            log.info("Account blocked: userId={}", userId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException("User not found: " + userId);
        } catch (HttpServerErrorException e) {
            log.error("Gateway error: {}", e.getMessage(), e);
            throw new GatewayException("Gateway error: " + e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Gateway unavailable: {}", e.getMessage(), e);
            throw new GatewayException("Gateway unavailable");
        }
    }

    public void unblockAccount(UUID userId) {
        try {
            restTemplate.put(gatewayUrl + "/account/unblock/" + userId, null);
            log.info("Account unblocked: userId={}", userId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException("User not found: " + userId);
        } catch (HttpServerErrorException e) {
            log.error("Gateway error: {}", e.getMessage(), e);
            throw new GatewayException("Gateway error: " + e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Gateway unavailable: {}", e.getMessage(), e);
            throw new GatewayException("Gateway unavailable");
        }
    }

    public PageAccountDto getAccountsPage(int page, int size, String sort) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(gatewayUrl + "/account")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .queryParam("sort", sort);

            ResponseEntity<PageAccountDto> response = restTemplate.getForEntity(
                    builder.toUriString(),
                    PageAccountDto.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new GatewayException("Failed to fetch accounts page");

        } catch (HttpServerErrorException e) {
            log.error("Gateway error: {}", e.getMessage(), e);
            throw new GatewayException("Gateway error: " + e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Gateway unavailable: {}", e.getMessage(), e);
            throw new GatewayException("Gateway unavailable");
        }
    }
}
