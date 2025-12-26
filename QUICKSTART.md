# Quick Start Guide

## Getting Started with Admin Bot Service

### Prerequisites

Before running the service, ensure you have:

1. **Telegram Bot Token**
   - Create a bot via [@BotFather](https://t.me/botfather)
   - Get your bot token and username
   - Add your Telegram user ID to admin whitelist

2. **Infrastructure**
   - PostgreSQL database
   - Redis instance
   - (Optional) Kafka for messaging
   - (Optional) Eureka for service discovery

### Quick Start with Docker Compose

1. **Clone the repository**
   ```bash
   git clone https://github.com/mrcreate163/mc-admin.git
   cd mc-admin
   ```

2. **Create .env file**
   ```bash
   cp .env.example .env
   ```
   
   Edit `.env` and set:
   - `TELEGRAM_BOT_TOKEN` - Your bot token from BotFather
   - `TELEGRAM_BOT_USERNAME` - Your bot username
   - `ADMIN_TELEGRAM_IDS` - Your Telegram user ID(s)

3. **Start all services**
   ```bash
   docker-compose up -d
   ```

4. **Check logs**
   ```bash
   docker-compose logs -f admin-bot-service
   ```

5. **Start chatting with your bot**
   - Open Telegram
   - Find your bot by username
   - Send `/start` to begin

### Running Locally (without Docker)

1. **Start required services**
   ```bash
   # PostgreSQL
   docker run -d --name postgres \
     -e POSTGRES_DB=social_network \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=password \
     -p 5432:5432 postgres:15-alpine

   # Redis
   docker run -d --name redis -p 6379:6379 redis:7-alpine
   ```

2. **Set environment variables**
   ```bash
   export TELEGRAM_BOT_TOKEN="your_token"
   export TELEGRAM_BOT_USERNAME="your_bot_username"
   export ADMIN_TELEGRAM_IDS="your_telegram_id"
   ```

3. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

### Getting Your Telegram User ID

To get your Telegram user ID:

1. Message [@userinfobot](https://t.me/userinfobot)
2. Copy your ID from the response
3. Add it to `ADMIN_TELEGRAM_IDS` in your configuration

### Testing the Bot

Once the bot is running, test these commands:

1. **Start the bot**
   ```
   /start
   ```
   Should display welcome message with menu

2. **View statistics** (if you have Gateway running)
   ```
   /stats
   ```
   Shows platform statistics

3. **View user** (requires valid UUID)
   ```
   /user 123e4567-e89b-12d3-a456-426614174000
   ```

4. **Block user** (requires valid UUID)
   ```
   /ban 123e4567-e89b-12d3-a456-426614174000
   ```

5. **Unblock user** (requires valid UUID)
   ```
   /unban 123e4567-e89b-12d3-a456-426614174000
   ```

### Troubleshooting

#### Bot doesn't respond
- Check bot token is correct
- Verify application is running
- Check logs for errors
- Ensure your Telegram ID is in whitelist

#### Database connection errors
- Verify PostgreSQL is running
- Check database credentials
- Ensure database exists
- Check Liquibase migrations ran successfully

#### Gateway connection errors
- Verify Gateway service is running
- Check GATEWAY_HOST configuration
- Ensure network connectivity

#### "Unauthorized" message
- Verify your Telegram user ID is in `ADMIN_TELEGRAM_IDS`
- Check configuration was loaded correctly
- Restart the service after configuration changes

### Development Tips

1. **View logs**
   ```bash
   docker-compose logs -f admin-bot-service
   ```

2. **Rebuild after code changes**
   ```bash
   docker-compose up -d --build
   ```

3. **Access database**
   ```bash
   docker exec -it admin-bot-postgres psql -U postgres -d social_network
   ```

4. **Access Redis**
   ```bash
   docker exec -it admin-bot-redis redis-cli
   ```

5. **Run tests**
   ```bash
   ./mvnw test
   ```

### Configuration Reference

Key configuration properties in `application.yml`:

| Property | Description | Default |
|----------|-------------|---------|
| `spring.application.name` | Service name | admin-bot-service |
| `spring.datasource.url` | PostgreSQL connection | jdbc:postgresql://localhost:5432/social_network |
| `telegram.bot.token` | Bot token from BotFather | - |
| `telegram.bot.username` | Bot username | - |
| `gateway.url` | Gateway API URL | http://localhost:8080/api/v1 |
| `admin.whitelist` | Comma-separated admin IDs | - |

### Next Steps

1. **Integration with Gateway**
   - Deploy API Gateway service
   - Configure Gateway URL
   - Test user management features

2. **Production Deployment**
   - Set up production database
   - Configure Redis cluster
   - Set up monitoring and logging
   - Configure SSL/TLS

3. **Extend Functionality**
   - Add more commands
   - Implement v2.0 features
   - Add custom admin actions

### Support

For issues or questions:
- Check the logs first
- Review the main README.md
- Check AI_CONTEXT.md for architecture details

## Architecture Overview

```
┌──────────────┐
│   Telegram   │
│     Bot      │
└──────┬───────┘
       │
       v
┌──────────────────────────────────┐
│   Admin Bot Service (This)       │
│  ┌────────────────────────────┐  │
│  │  Telegram Handlers         │  │
│  └────────────┬───────────────┘  │
│               v                  │
│  ┌────────────────────────────┐  │
│  │  Service Layer             │  │
│  └────────────┬───────────────┘  │
│               v                  │
│  ┌────────────────────────────┐  │
│  │  Gateway Clients           │  │
│  └────────────┬───────────────┘  │
└───────────────┼──────────────────┘
                │
                v
┌───────────────────────────────────┐
│        API Gateway                │
└───────────┬───────────────────────┘
            │
    ┌───────┴───────┐
    v               v
┌─────────┐   ┌─────────┐
│  Auth   │   │ Account │
│ Service │   │ Service │
└─────────┘   └─────────┘
```

## Database Schema

### admins
- Stores admin users who can use the bot
- Links to Telegram user ID

### audit_log
- Tracks all admin actions
- Includes action type, target user, and details
- Used for compliance and monitoring
