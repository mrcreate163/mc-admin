# Admin Bot Service – AI Context for GitHub Copilot

## 1. Общая информация о проекте

Telegram bot admin panel как отдельный микросервис для социальной сети (pet‑проект).  
Основная задача – управлять пользователями и базовой модерацией через Telegram бота.

**Технологический стек:**

- Java 21
- Spring Boot 3.x
- Spring Web (REST-клиенты к Gateway)
- Spring Data JPA + PostgreSQL
- Liquibase (миграции БД)
- Spring Data Redis (state machine, кеш)
- Spring Kafka (Version 3.0+, для последующих версий сервиса)
- Eureka Client (регистрация в Service Discovery)
- Telegram Bots (Java библиотека, Spring Boot starter)
- Maven
- Docker (контейнеризация)

Микросервис интегрируется с остальными через API Gateway (Spring Cloud Gateway).

---

## 2. Архитектура и структура проекта

Паттерн: классическая трёхслойная архитектура + отдельный слой для Telegram.

**Основные пакеты:**

    src/main/java/com/socialnetwork/adminbot/
    ├── config/
    │   ├── RestTemplateConfig.java
    │   ├── RedisConfig.java
    │   └── TelegramBotConfig.java
    ├── telegram/
    │   ├── handler/
    │   │   ├── UserCommandHandler.java
    │   │   ├── StatsCommandHandler.java
    │   │   └── CallbackQueryHandler.java
    │   ├── keyboard/
    │   │   └── KeyboardBuilder.java
    │   ├── state/
    │   │   └── BotStateManager.java
    │   └── TelegramBot.java
    ├── service/
    │   ├── AdminService.java
    │   ├── UserService.java
    │   ├── StatisticsService.java
    │   └── AuditService.java
    ├── repository/
    │   ├── AdminRepository.java
    │   └── AuditLogRepository.java
    ├── entity/
    │   ├── Admin.java
    │   └── AuditLog.java
    ├── dto/
    │   ├── AccountDto.java
    │   ├── PageAccountDto.java
    │   ├── AdminDto.java
    │   └── StatisticsDto.java
    ├── client/
    │   ├── AuthClient.java
    │   └── AccountClient.java
    ├── exception/
    │   ├── UnauthorizedException.java
    │   ├── UserNotFoundException.java
    │   └── GatewayException.java
    └── AdminBotApplication.java

---

## 3. Основные зависимости

Используются Spring Boot 3.x стартеры и дополнительные библиотеки для Telegram.

**Главные зависимости:**

- spring-boot-starter-web
- spring-boot-starter-data-jpa
- postgresql (driver)
- liquibase-core
- spring-boot-starter-data-redis
- spring-kafka
- spring-cloud-starter-netflix-eureka-client
- telegrambots-spring-boot-starter (version 6.9.7.1)
- lombok

---

## 4. Конфигурация application.yml

    spring:
      application:
        name: admin-bot-service
    
      datasource:
        url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:social_network}
        username: ${DB_USER:postgres}
        password: ${DB_PASSWORD:password}
    
      jpa:
        hibernate:
          ddl-auto: none
        show-sql: true
    
      liquibase:
        change-log: classpath:db/changelog/db.changelog-master.xml
    
      data:
        redis:
          host: ${REDIS_HOST:localhost}
          port: ${REDIS_PORT:6379}
    
      kafka:
        bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
        consumer:
          group-id: admin-bot-group
    
    eureka:
      client:
        service-url:
          defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
      instance:
        prefer-ip-address: true
    
    telegram:
      bot:
        token: ${TELEGRAM_BOT_TOKEN}
        username: ${TELEGRAM_BOT_USERNAME}
    
    gateway:
      url: http://${GATEWAY_HOST:localhost}:8080/api/v1
    
    admin:
      whitelist: ${ADMIN_TELEGRAM_IDS:123456789,987654321}

---

## 5. Интеграция с внешними сервисами

Все вызовы идут через API Gateway по адресу из конфига `gateway.url`.

### Auth Service – validate token

    GET {gatewayUrl}/auth/validate?token={token}
    Returns: Boolean

Пример кода:

    boolean isValid = restTemplate.getForObject(
        gatewayUrl + "/auth/validate?token=" + token,
        Boolean.class
    );

### Account Service – получить аккаунт по ID

    GET {gatewayUrl}/account/{id}
    Returns: AccountDto

Пример кода:

    AccountDto account = restTemplate.getForObject(
        gatewayUrl + "/account/" + userId,
        AccountDto.class
    );

### Account Service – блокировка пользователя

    PUT {gatewayUrl}/account/block/{id}
    Returns: String message

Пример кода:

    restTemplate.put(
        gatewayUrl + "/account/block/" + userId,
        null
    );

### Account Service – список с пагинацией

    GET {gatewayUrl}/account?page=0&size=10&sort=regDate,desc
    Returns: PageAccountDto

Пример кода:

    UriComponentsBuilder builder = UriComponentsBuilder
        .fromHttpUrl(gatewayUrl + "/account")
        .queryParam("page", page)
        .queryParam("size", size)
        .queryParam("sort", "regDate,desc");
    
    PageAccountDto result = restTemplate.getForObject(
        builder.toUriString(),
        PageAccountDto.class
    );

---

## 6. DTO примеры

### AccountDto

    @Data
    public class AccountDto {
        private UUID id;
        private String email;
        private String phone;
        private String photo;
        private String about;
        private String city;
        private String country;
        private String firstName;
        private String lastName;
        private LocalDateTime regDate;
        private LocalDate birthDate;
        private LocalDateTime lastOnlineTime;
        private Boolean isOnline;
        private Boolean isBlocked;
        private Boolean isDeleted;
    }

### PageAccountDto

    @Data
    public class PageAccountDto {
        private Long totalElements;
        private Integer totalPages;
        private Integer size;
        private List<AccountDto> content;
        private Integer number;
    }

---

## 7. Схема БД для Version 1.0

### Таблица admins

    CREATE TABLE admins (
        id BIGSERIAL PRIMARY KEY,
        telegram_user_id BIGINT UNIQUE NOT NULL,
        username VARCHAR(255),
        first_name VARCHAR(255),
        role VARCHAR(50) NOT NULL,
        is_active BOOLEAN DEFAULT TRUE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

### Таблица audit_log

    CREATE TABLE audit_log (
        id BIGSERIAL PRIMARY KEY,
        admin_id BIGINT REFERENCES admins(id),
        action_type VARCHAR(100) NOT NULL,
        target_user_id UUID,
        details JSONB,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

---

## 8. Пример Entity класса

    @Entity
    @Table(name = "admins")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public class Admin {
    
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
    
        @Column(name = "telegram_user_id", unique = true, nullable = false)
        private Long telegramUserId;
    
        @Column(name = "username")
        private String username;
    
        @Column(name = "first_name")
        private String firstName;
    
        @Enumerated(EnumType.STRING)
        @Column(name = "role", nullable = false)
        private AdminRole role;
    
        @Column(name = "is_active")
        private Boolean isActive = true;
    
        @Column(name = "created_at")
        private LocalDateTime createdAt;
    
        @Column(name = "updated_at")
        private LocalDateTime updatedAt;
    
        @PrePersist
        protected void onCreate() {
            createdAt = LocalDateTime.now();
            updatedAt = LocalDateTime.now();
        }
    
        @PreUpdate
        protected void onUpdate() {
            updatedAt = LocalDateTime.now();
        }
    }

---

## 9. Пример Repository

    @Repository
    public interface AdminRepository extends JpaRepository<Admin, Long> {
    
        Optional<Admin> findByTelegramUserId(Long telegramUserId);
    
        boolean existsByTelegramUserId(Long telegramUserId);
    
        List<Admin> findByIsActiveTrue();
    }

---

## 10. Пример Service класса

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class AdminService {
    
        private final AdminRepository adminRepository;
    
        public Admin findByTelegramId(Long telegramUserId) {
            return adminRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> {
                    log.warn("Admin not found: telegramUserId={}", telegramUserId);
                    return new UnauthorizedException("Admin not found");
                });
        }
    
        public boolean isAdmin(Long telegramUserId) {
            return adminRepository.existsByTelegramUserId(telegramUserId);
        }
    
        @Transactional
        public Admin createAdmin(AdminDto dto) {
            if (adminRepository.existsByTelegramUserId(dto.getTelegramUserId())) {
                throw new DuplicateAdminException("Admin already exists");
            }
    
            Admin admin = Admin.builder()
                .telegramUserId(dto.getTelegramUserId())
                .username(dto.getUsername())
                .firstName(dto.getFirstName())
                .role(dto.getRole())
                .isActive(true)
                .build();
    
            Admin saved = adminRepository.save(admin);
            log.info("Created new admin: id={} telegramUserId={}", 
                saved.getId(), saved.getTelegramUserId());
            return saved;
        }
    }

---

## 11. Telegram Handler примеры

### Command Handler для /user

    @Component
    @RequiredArgsConstructor
    public class UserCommandHandler {
    
        private final UserService userService;
        private final AuditService auditService;
    
        public SendMessage handle(Message message, Long adminTelegramId) {
            String[] parts = message.getText().split(" ");
            if (parts.length < 2) {
                return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Usage: /user <user_id>"
                );
            }
    
            UUID userId = UUID.fromString(parts[1]);
            AccountDto account = userService.getUserById(userId);
    
            auditService.log(adminTelegramId, "VIEW_USER", userId, Map.of());
    
            return buildUserInfoMessage(message.getChatId(), account);
        }
    
        private SendMessage buildUserInfoMessage(Long chatId, AccountDto account) {
            String text = String.format(
                "👤 %s %s\nID: %s\nEmail: %s\nCity: %s\nBlocked: %s",
                account.getFirstName(),
                account.getLastName(),
                account.getId(),
                account.getEmail(),
                account.getCity(),
                Boolean.TRUE.equals(account.getIsBlocked()) ? "Yes" : "No"
            );
    
            SendMessage message = new SendMessage(chatId.toString(), text);
            message.setReplyMarkup(
                KeyboardBuilder.buildUserActionsKeyboard(
                    account.getId(), 
                    Boolean.TRUE.equals(account.getIsBlocked())
                )
            );
            return message;
        }
    }

### InlineKeyboard Builder

    public class KeyboardBuilder {
    
        public static InlineKeyboardMarkup buildUserActionsKeyboard(
            UUID userId, 
            boolean isBlocked
        ) {
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(InlineKeyboardButton.builder()
                .text("📊 Statistics")
                .callbackData("stats:" + userId)
                .build());
            row1.add(InlineKeyboardButton.builder()
                .text(isBlocked ? "✅ Unblock" : "🚫 Block")
                .callbackData((isBlocked ? "unblock:" : "block:") + userId)
                .build());
            rows.add(row1);
    
            List<InlineKeyboardButton> row2 = List.of(
                InlineKeyboardButton.builder()
                    .text("« Back to Menu")
                    .callbackData("main_menu")
                    .build()
            );
            rows.add(row2);
    
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            keyboard.setKeyboard(rows);
            return keyboard;
        }
    }

---

## 12. Обработка ошибок

    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class UserService {
    
        private final RestTemplate restTemplate;
        
        @Value("${gateway.url}")
        private String gatewayUrl;
    
        public AccountDto getUserById(UUID userId) {
            try {
                ResponseEntity<AccountDto> response = restTemplate.getForEntity(
                    gatewayUrl + "/account/" + userId,
                    AccountDto.class
                );
    
                if (response.getStatusCode().is2xxSuccessful() 
                    && response.getBody() != null) {
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
    }

---

## 13. Custom Exceptions

    public class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
    
    public class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
    
    public class GatewayException extends RuntimeException {
        public GatewayException(String message) {
            super(message);
        }
    }

---

## 14. Стиль кода и conventions

**Общие правила:**

- Всегда используй Lombok аннотации: @Data, @Builder, @RequiredArgsConstructor, @Slf4j
- Методы сервисов с понятными именами: getUserById(), blockUser(), logAction()
- Не кидай общий Exception, только специфичные RuntimeException
- Telegram handlers должны быть тонкими, бизнес-логика в сервисах
- DTO только для передачи данных, без бизнес-логики
- UUID для id пользователей, Long для внутренних id
- Логируй все важные операции: log.info(), log.warn(), log.error()
- Валидируй входные данные перед обработкой
- Используй Optional для потенциально null значений
- Константы для типов действий, текстов кнопок
- Все операции с БД в транзакциях (@Transactional)

**Naming conventions:**

- Entity: Admin, AuditLog
- Repository: AdminRepository, AuditLogRepository
- Service: AdminService, UserService
- Handler: UserCommandHandler, CallbackQueryHandler
- DTO: AccountDto, PageAccountDto
- Exception: UnauthorizedException, UserNotFoundException

---

## 15. Version 1.0 MVP - Главные задачи

**Обязательный функционал для первой версии:**

1. Базовая аутентификация администраторов (whitelist в application.yml)
2. Регистрация в Eureka
3. Команды: /start, /user {id}, /ban {id}, /unban {id}, /stats
4. Просмотр информации о пользователе через Gateway
5. Блокировка/разблокировка пользователей
6. Простая статистика текстом (количество пользователей, новых за день)
7. Логирование всех действий в audit_log таблицу
8. Inline-меню с базовыми кнопками
9. Обработка ошибок (пользователь не найден, Gateway недоступен)

**Что НЕ входит в v1.0:**

- Kafka интеграция (будет в v3.0)
- Графики через QuickChart (будет в v2.0)
- Управление администраторами через БД (будет в v2.0)
- State Machine через Redis (будет в v2.0)

---

Конец файла
