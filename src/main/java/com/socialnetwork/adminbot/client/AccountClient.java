package com.socialnetwork.adminbot.client;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.PageAccountDto;
import com.socialnetwork.adminbot.exception.ServiceException;
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

/**
 * HTTP клиент для взаимодействия с mc-account через internal API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClient {

    private final RestTemplate restTemplate;

    @Value("${services.account.url}")
    private String accountServiceUrl;

    /**
     * Получить аккаунт по ID
     */
    public AccountDto getAccountById(UUID userId) {
        try {
            String url = accountServiceUrl + "/" + userId;
            log.debug("Fetching account: GET {}", url);

            ResponseEntity<AccountDto> response = restTemplate.getForEntity(
                    url,
                    AccountDto.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Account fetched successfully: userId={}", userId);
                return response.getBody();
            }

            throw new UserNotFoundException("User not found: " + userId);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Account not found: userId={}", userId);
            throw new UserNotFoundException("User not found: " + userId);

        } catch (HttpServerErrorException e) {
            log.error("Account service error: {} {}", e.getStatusCode(), e.getMessage());
            throw new ServiceException("Account service error: " + e.getMessage());

        } catch (ResourceAccessException e) {
            log.error("Account service unavailable: {}", e.getMessage());
            throw new ServiceException("Account service unavailable");
        }
    }

    /**
     * Заблокировать аккаунт
     */
    public void blockAccount(UUID userId) {
        try {
            String url = accountServiceUrl + "/block/" + userId;
            log.info("Blocking account: PUT {}", url);

            restTemplate.put(url, null);

            log.info("Account blocked successfully: userId={}", userId);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Cannot block - account not found: userId={}", userId);
            throw new UserNotFoundException("User not found: " + userId);

        } catch (HttpServerErrorException e) {
            log.error("Account service error while blocking: {} {}", e.getStatusCode(), e.getMessage());
            throw new ServiceException("Account service error: " + e.getMessage());

        } catch (ResourceAccessException e) {
            log.error("Account service unavailable while blocking: {}", e.getMessage());
            throw new ServiceException("Account service unavailable");
        }
    }

    /**
     * Разблокировать аккаунт
     */
    public void unblockAccount(UUID userId) {
        try {
            String url = accountServiceUrl + "/block/" + userId;
            log.info("Unblocking account: DELETE {}", url);

            restTemplate.delete(url);

            log.info("Account unblocked successfully: userId={}", userId);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Cannot unblock - account not found: userId={}", userId);
            throw new UserNotFoundException("User not found: " + userId);

        } catch (HttpServerErrorException e) {
            log.error("Account service error while unblocking: {} {}", e.getStatusCode(), e.getMessage());
            throw new ServiceException("Account service error: " + e.getMessage());

        } catch (ResourceAccessException e) {
            log.error("Account service unavailable while unblocking: {}", e.getMessage());
            throw new ServiceException("Account service unavailable");
        }
    }

    /**
     * Получить страницу аккаунтов с пагинацией
     */
    public PageAccountDto getAccountsPage(int page, int size, String sort) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(accountServiceUrl)
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .queryParam("sort", sort);

            String url = builder.toUriString();
            log.debug("Fetching accounts page: GET {}", url);

            ResponseEntity<PageAccountDto> response = restTemplate.getForEntity(
                    url,
                    PageAccountDto.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                PageAccountDto pageAccountDto = response.getBody();
                log.debug("Accounts page fetched: totalElements={}, totalPages={}",
                        pageAccountDto.getTotalElements(), pageAccountDto.getTotalPages());
                return pageAccountDto;
            }

            throw new ServiceException("Failed to fetch accounts page");

        } catch (HttpServerErrorException e) {
            log.error("Account service error: {} {}", e.getStatusCode(), e.getMessage());
            throw new ServiceException("Account service error: " + e.getMessage());

        } catch (ResourceAccessException e) {
            log.error("Account service unavailable: {}", e.getMessage());
            throw new ServiceException("Account service unavailable");
        }
    }
}
