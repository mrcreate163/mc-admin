# Admin Bot Service

Telegram bot admin panel microservice for social network (pet-project). Manages users and basic moderation through Telegram bot.

## Technology Stack

- **Java 17**
- **Spring Boot 4.0.1**
- **Spring Web** (REST clients to Gateway)
- **Spring Data JPA** + PostgreSQL
- **Liquibase** (database migrations)
- **Spring Data Redis** (state machine, cache)
- **Spring Kafka** (for future versions)
- **Eureka Client** (service discovery)
- **Telegram Bots** (Java library)
- **Maven**
- **Docker** (containerization)

## Architecture

Classic three-layer architecture + separate Telegram layer:

```
src/main/java/com/socialnetwork/adminbot/
├── config/          - Configuration classes
├── telegram/        - Telegram bot and handlers
├── service/         - Business logic layer
├── repository/      - Data access layer
├── entity/          - JPA entities
├── dto/             - Data transfer objects
├── client/          - External service clients
└── exception/       - Custom exceptions
```

## Features (v1.0 MVP)

### Telegram Commands

- `/start` - Show welcome message and main menu
- `/user <user_id>` - View user information
- `/ban <user_id>` - Block user
- `/unban <user_id>` - Unblock user
- `/stats` - View platform statistics

### Admin Features

- **User Management**: View user information, block/unblock users
- **Statistics**: Total users, new users today, blocked users
- **Audit Logging**: All admin actions are logged to database
- **Inline Keyboards**: Interactive buttons for quick actions
- **Authorization**: Whitelist-based admin access control

## Configuration

### Environment Variables

Create `.env` file or set environment variables:

```bash
# Database
DB_HOST=localhost
DB_NAME=social_network
DB_USER=postgres
DB_PASSWORD=password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_SERVERS=localhost:9092

# Eureka
EUREKA_HOST=localhost

# Telegram Bot
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_BOT_USERNAME=your_bot_username

# Gateway
GATEWAY_HOST=localhost

# Admin Whitelist (comma-separated Telegram user IDs)
ADMIN_TELEGRAM_IDS=123456789,987654321
```

### Application Configuration

See `src/main/resources/application.yml` for full configuration.

## Database Schema

### admins table

Stores administrator information:

- `id` - Primary key
- `telegram_user_id` - Telegram user ID (unique)
- `username` - Telegram username
- `first_name` - First name
- `role` - Admin role (SUPER_ADMIN, ADMIN, MODERATOR)
- `is_active` - Active status
- `created_at` - Creation timestamp
- `updated_at` - Update timestamp

### audit_log table

Stores audit logs of admin actions:

- `id` - Primary key
- `admin_id` - Reference to admin
- `action_type` - Type of action performed
- `target_user_id` - Target user UUID
- `details` - Additional details (JSONB)
- `created_at` - Action timestamp

## Building and Running

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+
- Redis 6+
- Kafka (optional for v1.0)

### Build

```bash
./mvnw clean package
```

### Run

```bash
./mvnw spring-boot:run
```

### Run with Docker

```bash
# Build image
docker build -t admin-bot-service .

# Run container
docker run -d \
  -e TELEGRAM_BOT_TOKEN=your_token \
  -e TELEGRAM_BOT_USERNAME=your_username \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  admin-bot-service
```

## Testing

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

## Integration with Services

All external calls go through API Gateway at `gateway.url`:

### Auth Service

- `GET /auth/validate?token={token}` - Validate authentication token

### Account Service

- `GET /account/{id}` - Get account by ID
- `PUT /account/block/{id}` - Block user
- `PUT /account/unblock/{id}` - Unblock user
- `GET /account?page=0&size=10&sort=regDate,desc` - Get paginated accounts

## Development

### Code Style

- Use Lombok annotations: `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`
- Service methods with clear names: `getUserById()`, `blockUser()`, `logAction()`
- Don't throw generic Exception, use specific RuntimeException
- Telegram handlers should be thin, business logic in services
- UUID for user IDs, Long for internal IDs
- Log all important operations
- All database operations in transactions (`@Transactional`)

### Adding New Commands

1. Create handler in `telegram/handler/` package
2. Implement command logic calling services
3. Register handler in `TelegramBot` class
4. Add audit logging for the action

## Roadmap

### v1.0 (Current) ✅
- Basic admin authentication (whitelist)
- User management commands
- Statistics
- Audit logging
- Inline keyboard menus

### v2.0 (Planned)
- Admin management via database
- State machine with Redis
- Enhanced statistics with charts
- More moderation features

### v3.0 (Planned)
- Kafka integration for events
- Real-time notifications
- Advanced analytics

## License

This is a pet project for educational purposes.

## Authors

Pet project for social network
