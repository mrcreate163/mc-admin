package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.dto.PendingInvitation;
import com.socialnetwork.adminbot.entity.AdminRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteService Unit Tests")
class InviteServiceTest {

    @Mock
    private RedisTemplate<String, PendingInvitation> pendingInvitationRedisTemplate;

    @Mock
    private ValueOperations<String, PendingInvitation> valueOperations;

    private InviteService inviteService;

    private PendingInvitation testInvitation;
    private static final String TEST_TOKEN = "1234567890abcdef";
    private static final Long INVITED_BY = 123456789L;
    private static final String TEST_NOTE = "Test note";

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(pendingInvitationRedisTemplate);

        testInvitation = PendingInvitation.builder()
                .inviteToken(TEST_TOKEN)
                .invitedBy(INVITED_BY)
                .role(AdminRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .note(TEST_NOTE)
                .build();
    }

    @Nested
    @DisplayName("createInvitation Tests")
    class CreateInvitationTests {

        @Test
        @DisplayName("should create invitation with role only")
        void createInvitation_WithRoleOnly_ShouldSaveAndReturnToken() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            String token = inviteService.createInvitation(INVITED_BY, AdminRole.ADMIN);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).hasSize(16);

            ArgumentCaptor<PendingInvitation> invitationCaptor = ArgumentCaptor.forClass(PendingInvitation.class);
            verify(valueOperations).set(eq("telegram:invite:" + token), invitationCaptor.capture(), eq(Duration.ofHours(24)));

            PendingInvitation savedInvitation = invitationCaptor.getValue();
            assertThat(savedInvitation.getInviteToken()).isEqualTo(token);
            assertThat(savedInvitation.getInvitedBy()).isEqualTo(INVITED_BY);
            assertThat(savedInvitation.getRole()).isEqualTo(AdminRole.ADMIN);
            assertThat(savedInvitation.getNote()).isNull();
        }

        @Test
        @DisplayName("should create invitation with note")
        void createInvitation_WithNote_ShouldSaveWithNote() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            String token = inviteService.createInvitation(INVITED_BY, AdminRole.MODERATOR, TEST_NOTE);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).hasSize(16);

            ArgumentCaptor<PendingInvitation> invitationCaptor = ArgumentCaptor.forClass(PendingInvitation.class);
            verify(valueOperations).set(eq("telegram:invite:" + token), invitationCaptor.capture(), eq(Duration.ofHours(24)));

            PendingInvitation savedInvitation = invitationCaptor.getValue();
            assertThat(savedInvitation.getRole()).isEqualTo(AdminRole.MODERATOR);
            assertThat(savedInvitation.getNote()).isEqualTo(TEST_NOTE);
        }
    }

    @Nested
    @DisplayName("getInvitation Tests")
    class GetInvitationTests {

        @Test
        @DisplayName("should return invitation when token exists")
        void getInvitation_WhenExists_ShouldReturnInvitation() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("telegram:invite:" + TEST_TOKEN)).thenReturn(testInvitation);

            // When
            Optional<PendingInvitation> result = inviteService.getInvitation(TEST_TOKEN);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getInviteToken()).isEqualTo(TEST_TOKEN);
            assertThat(result.get().getInvitedBy()).isEqualTo(INVITED_BY);
            assertThat(result.get().getRole()).isEqualTo(AdminRole.ADMIN);
        }

        @Test
        @DisplayName("should return empty when token not found")
        void getInvitation_WhenNotFound_ShouldReturnEmpty() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("telegram:invite:" + TEST_TOKEN)).thenReturn(null);

            // When
            Optional<PendingInvitation> result = inviteService.getInvitation(TEST_TOKEN);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("consumeInvitation Tests")
    class ConsumeInvitationTests {

        @Test
        @DisplayName("should consume and delete invitation when exists")
        void consumeInvitation_WhenExists_ShouldReturnAndDelete() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("telegram:invite:" + TEST_TOKEN)).thenReturn(testInvitation);

            // When
            Optional<PendingInvitation> result = inviteService.consumeInvitation(TEST_TOKEN);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getInviteToken()).isEqualTo(TEST_TOKEN);
            verify(pendingInvitationRedisTemplate).delete("telegram:invite:" + TEST_TOKEN);
        }

        @Test
        @DisplayName("should return empty when invitation not found")
        void consumeInvitation_WhenNotFound_ShouldReturnEmpty() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("telegram:invite:" + TEST_TOKEN)).thenReturn(null);

            // When
            Optional<PendingInvitation> result = inviteService.consumeInvitation(TEST_TOKEN);

            // Then
            assertThat(result).isEmpty();
            verify(pendingInvitationRedisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("isValidInvitation Tests")
    class IsValidInvitationTests {

        @Test
        @DisplayName("should return true when invitation exists")
        void isValidInvitation_WhenExists_ShouldReturnTrue() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("telegram:invite:" + TEST_TOKEN)).thenReturn(testInvitation);

            // When
            boolean result = inviteService.isValidInvitation(TEST_TOKEN);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when invitation not found")
        void isValidInvitation_WhenNotFound_ShouldReturnFalse() {
            // Given
            when(pendingInvitationRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("telegram:invite:" + TEST_TOKEN)).thenReturn(null);

            // When
            boolean result = inviteService.isValidInvitation(TEST_TOKEN);

            // Then
            assertThat(result).isFalse();
        }
    }
}
