# Admin Bot Service

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Java Version](https://img.shields.io/badge/Java-17-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-green)]()
[![License](https://img.shields.io/badge/License-MIT-blue)]()

Telegram-бот для администрирования социальной сети. Микросервис обеспечивает управление пользователями и базовую модерацию через Telegram-бота.

## 📋 Содержание

- [Технологический стек](#технологический-стек)
- [Архитектура](#архитектура)
- [State Machine](#state-machine)
- [Возможности](#возможности-v20)
- [Быстрый старт](#быстрый-старт)
- [Конфигурация](#конфигурация)
- [База данных](#база-данных)
- [Тестирование](#тестирование)
- [Развёртывание](#развёртывание)
- [API интеграция](#интеграция-с-сервисами)
- [Разработка](#разработка)
- [Roadmap](#roadmap)

## 🛠 Технологический стек

| Категория | Технология                      |
|-----------|---------------------------------|
| **Язык** | Java 17                         |
| **Фреймворк** | Spring Boot 4.0.1               |
| **Web** | Spring Web (REST клиенты)       |
| **Данные** | Spring Data JPA + PostgreSQL    |
| **Миграции** | Liquibase                       |
| **Кеш/State** | Spring Data Redis (State Machine) |
| **Сообщения** | Spring Kafka (для v3.0+)        |
| **Telegram** | TelegramBots 6.9.7.1            |
| **Сборка** | Maven                           |
| **Контейнеризация** | Docker                          |
| **CI/CD** | GitLab CI                       |

## 🏗 Архитектура

Классическая трёхслойная архитектура с выделенным слоем для Telegram и State Machine:

```
src/main/java/com/socialnetwork/adminbot/
├── config/          # Конфигурация Spring beans
│   ├── RedisConfig.java
│   ├── RestTemplateConfig.java
│   └── TelegramBotConfig.java
├── controller/      # REST контроллеры
│   └── AdminRegistrationController.java  # API для регистрации администраторов
├── domain/          # Модели State Machine
│   ├── BotState.java           # Перечисление состояний
│   ├── ConversationState.java  # Модель состояния диалога
│   ├── StateDataKey.java       # Константы ключей данных
│   └── InviteToken.java        # Модель токена приглашения
├── telegram/        # Telegram бот и обработчики
│   ├── handler/     # Обработчики команд
│   │   ├── base/    # Базовые классы handlers
│   │   │   ├── BaseCommandHandler.java
│   │   │   ├── StatefulCommandHandler.java
│   │   │   └── StatelessCommandHandler.java
│   │   ├── StartCommandHandler.java    # Включает обработку deep link приглашений
│   │   ├── UserCommandHandler.java
│   │   ├── BanCommandHandler.java
│   │   ├── SearchCommandHandler.java
│   │   ├── AddAdminCommandHandler.java # Создание ссылок-приглашений
│   │   ├── TextMessageHandler.java
│   │   └── CallbackQueryHandler.java
│   ├── keyboard/    # Inline клавиатуры
│   ├── messages/    # Шаблоны сообщений
│   └── TelegramBot.java
├── service/         # Бизнес-логика
│   ├── AdminService.java
│   ├── UserService.java
│   ├── StatisticsService.java
│   ├── AuditLogService.java
│   ├── InviteService.java              # Управление приглашениями
│   ├── ConversationStateService.java   # Управление состояниями в Redis
│   └── StateTransitionService.java     # Валидация переходов
├── repository/      # Слой доступа к данным
│   ├── AdminRepository.java
│   ├── AuditLogRepository.java
│   └── AdminInvitationRepository.java  # Репозиторий приглашений
├── entity/          # JPA сущности
│   ├── Admin.java
│   ├── AdminRole.java
│   ├── AdminInvitation.java            # Сущность приглашения
│   └── AuditLog.java
├── dto/             # Data Transfer Objects
├── client/          # HTTP клиенты внешних сервисов
└── exception/       # Кастомные исключения
```

### Схема взаимодействия

```
┌──────────────┐      ┌──────────────────┐      ┌──────────────────┐
│   Telegram   │ ───► │  Admin Bot       │ ───► │  mc-account      │
│   Bot API    │ ◄─── │  Service         │ ◄─── │  (User Service)  │
└──────────────┘      └──────────────────┘      └──────────────────┘
                              │  │
                              │  └─────────────────┐
                              ▼                    ▼
                      ┌──────────────────┐  ┌──────────────────┐
                      │   PostgreSQL     │  │      Redis       │
                      │   (admins,       │  │   (State Machine │
                      │    audit_log)    │  │    States)       │
                      └──────────────────┘  └──────────────────┘
```

## 🔄 State Machine

### Описание

State Machine реализован через Redis для управления многоэтапными диалогами с пользователями. Каждый пользователь имеет своё состояние, которое хранится в Redis с TTL 30 минут.

### Состояния (BotState)

| Состояние | Описание |
|-----------|----------|
| `IDLE` | Начальное состояние - пользователь не в диалоге |
| `AWAITING_SEARCH_QUERY` | Ожидание ввода поискового запроса |
| `SHOWING_SEARCH_RESULTS` | Отображение результатов поиска (с пагинацией) |
| `AWAITING_BAN_REASON` | Ожидание причины бана пользователя |
| `CONFIRMING_BAN` | Подтверждение бана пользователя |
| `AWAITING_ADMIN_ROLE` | Ожидание выбора роли для нового админа (/addadmin) |
| `CONFIRMING_ADMIN_INVITE_CREATION` | Подтверждение создания приглашения |
| `CONFIRMING_INVITE_ACCEPTANCE` | Подтверждение активации приглашения (со стороны кандидата) |

### Диаграмма переходов

```
                           ┌────────────────────────────────────────┐
                           │                                        │
                           ▼                                        │
                       ┌──────┐                                     │
           ┌──────────►│ IDLE │◄────────────────────────────────────┤
           │           └──────┘                                     │
           │               │                                        │
           │    ┌──────────┼──────────┐                             │
           │    ▼          ▼          ▼                             │
           │ ┌────────┐ ┌────────┐ ┌────────┐                       │
           │ │AWAITING│ │AWAITING│ │AWAITING│                       │
           │ │ SEARCH │ │  BAN   │ │ ADMIN  │                       │
           │ │ QUERY  │ │ REASON │ │TELEGRAM│                       │
           │ └────────┘ └────────┘ └────────┘                       │
           │     │           │          │                           │
           │     ▼           ▼          ▼                           │
           │ ┌────────┐ ┌────────┐ ┌────────┐                       │
           │ │SHOWING │ │CONFIRM │ │AWAITING│                       │
           │ │ SEARCH │ │  BAN   │ │ ADMIN  │                       │
           │ │RESULTS │ │        │ │  ROLE  │                       │
           │ └────────┘ └────────┘ └────────┘                       │
           │     │           │          │                           │
           │     │           │          ▼                           │
           │     │           │     ┌────────┐                       │
           │     │           │     │CONFIRM │                       │
           │     │           │     │ ADMIN  │                       │
           │     │           │     │CREATION│                       │
           │     │           │     └────────┘                       │
           │     │           │          │                           │
           └─────┴───────────┴──────────┴───────────────────────────┘
```

### Типы Handler'ов

- **StatelessCommandHandler** - для простых команд без диалога (`/start`, `/stats`, `/help`)
- **StatefulCommandHandler** - для диалоговых команд (`/search`, `/ban`)

### Ключевые компоненты

| Компонент | Описание |
|-----------|----------|
| `ConversationState` | Модель состояния с данными контекста и версионированием |
| `ConversationStateService` | CRUD операции для состояний в Redis |
| `StateTransitionService` | Валидация разрешённых переходов между состояниями |
| `TextMessageHandler` | Роутинг текстовых сообщений по текущему состоянию |

## ⭐ Возможности (v2.2)

### Telegram команды

| Команда | Описание |
|---------|----------|
| `/start` | Приветственное сообщение и главное меню |
| `/start invite_<token>` | Активация по пригласительной ссылке (deep link) |
| `/user <id>` | Просмотр информации о пользователе |
| `/ban <id>` | Заблокировать пользователя (с выбором причины) |
| `/unban <id>` | Разблокировать пользователя |
| `/search [query]` | Поиск пользователей по email |
| `/stats` | Статистика платформы |
| `/addadmin` | Создать ссылку-приглашение для нового администратора (только SUPER_ADMIN) |
| `/cancel` | Отменить текущее действие |

### Функции администратора

- ✅ **Управление пользователями**: просмотр, блокировка/разблокировка
- ✅ **Поиск пользователей**: поиск по email с пагинацией
- ✅ **Блокировка с причиной**: выбор причины из предустановленных или ввод своей
- ✅ **State Machine**: многоэтапные диалоги с хранением состояния в Redis
- ✅ **Статистика**: общее количество, новые за сегодня, заблокированные
- ✅ **Аудит**: все действия администраторов логируются в БД
- ✅ **Inline-клавиатуры**: интерактивные кнопки для быстрых действий
- ✅ **Авторизация**: доступ на основе whitelist Telegram ID или БД
- ✅ **Приглашение администраторов**: SUPER_ADMIN может создавать одноразовые ссылки-приглашения

### Система приглашений (v2.2)

Механизм добавления новых администраторов через одноразовые ссылки-приглашения:

**Процесс:**
1. SUPER_ADMIN выполняет команду `/addadmin`
2. Выбирает роль для нового администратора (MODERATOR, SENIOR_MODERATOR, ADMIN)
3. Получает уникальную ссылку формата `https://t.me/BotName?start=invite_<token>`
4. Отправляет ссылку новому администратору
5. Новый администратор переходит по ссылке и автоматически активируется

**Особенности:**
- Ссылки действительны 24 часа
- Каждая ссылка одноразовая
- Можно назначать только роли ниже своей
- Все действия логируются в audit_log

## 🚀 Быстрый старт

### Требования

- Java 17+
- Maven 3.6+
- PostgreSQL 15+
- Redis 7+
- Docker (опционально)

### Локальный запуск

```bash
# 1. Клонирование репозитория
git clone https://github.com/your-org/mc-admin.git
cd mc-admin

# 2. Запуск инфраструктуры
docker-compose up -d postgres redis

# 3. Сборка проекта
./mvnw clean package -DskipTests

# 4. Запуск приложения
./mvnw spring-boot:run
```

### Запуск в Docker

```bash
# Сборка образа
docker build -t admin-bot-service .

# Запуск контейнера
docker run -d \
  --name admin-bot \
  --network social-network-net \
  -e TELEGRAM_BOT_TOKEN=your_token \
  -e TELEGRAM_BOT_USERNAME=your_bot \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/admin_bot_db \
  -e SPRING_DATASOURCE_PASSWORD=your_password \
  admin-bot-service
```

### Docker Compose (полный стек)

```bash
docker-compose up -d
```

## ⚙️ Конфигурация

### Переменные окружения

| Переменная                   | Описание                           | По умолчанию                                   |
|------------------------------|------------------------------------|------------------------------------------------|
| `SPRING_DATASOURCE_URL`      | JDBC URL PostgreSQL                | `jdbc:postgresql://postgres:5432/admin_bot_db` |
| `SPRING_DATASOURCE_USERNAME` | Пользователь Postgres              | `postgres`                                     |
| `SPRING_DATASOURCE_PASSWORD` | Пароль Postgres                    | -                                              |
| `REDIS_PASSWORD`             | Пароль Redis                       | -                                              |
| `TELEGRAM_BOT_TOKEN`         | Токен Telegram бота                | -                                              |
| `TELEGRAM_BOT_USERNAME`      | Username бота                      | -                                              |
| `ADMIN_WHITELIST`            | Список Telegram ID администраторов | `123456789`                                    |
| `ACCOUNT_SERVICE_URL`        | URL сервиса аккаунтов              | `http://mc-account:8080/internal/account`      |

### Пример .env файла

```bash
# База данных
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/admin_bot_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password

# Telegram бот
TELEGRAM_BOT_TOKEN=your_bot_token_here
TELEGRAM_BOT_USERNAME=YourBotUsername

# Администраторы (Telegram ID через запятую)
ADMIN_WHITELIST=123456789,987654321

# Сервис аккаунтов
ACCOUNT_SERVICE_URL=http://mc-account:8080/internal/account
```

## 🗄 База данных

### Таблица `admins`

| Колонка            | Тип          | Описание                                         |
|--------------------|--------------|--------------------------------------------------|
| `telegram_user_id` | BIGINT       | Telegram ID (primary key)                        |
| `username`         | VARCHAR(255) | Telegram username                                |
| `first_name`       | VARCHAR(255) | Имя                                              |
| `role`             | VARCHAR(50)  | Роль (SUPER_ADMIN, ADMIN, SENIOR_MODERATOR, MODERATOR) |
| `is_active`        | BOOLEAN      | Активен                                          |
| `invited_by`       | BIGINT       | Telegram ID пригласившего SUPER_ADMIN (NULL для whitelist) |
| `created_at`       | TIMESTAMP    | Дата создания                                    |
| `updated_at`       | TIMESTAMP    | Дата обновления                                  |

### Таблица `admin_invitations`

| Колонка              | Тип          | Описание                               |
|----------------------|--------------|----------------------------------------|
| `id`                 | UUID         | Первичный ключ                         |
| `invite_token`       | VARCHAR(64)  | Уникальный токен приглашения           |
| `role`               | VARCHAR(50)  | Роль для нового администратора         |
| `created_by`         | BIGINT       | Telegram ID создавшего SUPER_ADMIN     |
| `expires_at`         | TIMESTAMP    | Время истечения (24 часа от создания)  |
| `is_used`            | BOOLEAN      | Использовано ли приглашение            |
| `activated_admin_id` | BIGINT       | Telegram ID активированного админа     |
| `created_at`         | TIMESTAMP    | Время создания                         |
| `used_at`            | TIMESTAMP    | Время активации                        |

### Таблица `audit_log`

| Колонка          | Тип          | Описание                   |
|------------------|--------------|----------------------------|
| `id`             | UUID         | Первичный ключ             |
| `admin_id`       | BIGINT       | Telegram ID администратора |
| `action_type`    | VARCHAR(100) | Тип действия               |
| `target_user_id` | UUID         | ID целевого пользователя   |
| `details`        | JSONB        | Дополнительные данные      |
| `created_at`     | TIMESTAMP    | Время действия             |

### Миграции

Миграции управляются через Liquibase:

```
src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── v1.0/
    ├── 001-create-admins-table.yml
    ├── 002-create-audit-log-table.yml
    ├── 003-add-invited-by-to-admins.yml
    └── 004-create-admin-invitations-table.yml
```

## 🧪 Тестирование

### Запуск тестов

```bash
# Все тесты
./mvnw test

# Конкретный тест класс
./mvnw test -Dtest=AdminServiceTest

# С отчётом о покрытии
./mvnw test jacoco:report
```

### Структура тестов

```
src/test/java/com/socialnetwork/adminbot/
├── AdminBotApplicationTests.java    # Интеграционный тест контекста
├── client/
│   └── AccountClientTest.java       # Тесты HTTP клиента
├── domain/
│   └── ConversationStateTest.java   # Тесты модели состояния
├── service/
│   ├── AdminServiceTest.java        # Тесты сервиса админов
│   ├── UserServiceTest.java         # Тесты сервиса пользователей
│   ├── AuditLogServiceTest.java     # Тесты аудит логов
│   ├── StatisticsServiceTest.java   # Тесты статистики
│   ├── InviteServiceTest.java       # Тесты сервиса приглашений
│   ├── ConversationStateServiceTest.java  # Тесты State Machine сервиса
│   └── StateTransitionServiceTest.java   # Тесты валидации переходов
├── telegram/handler/
│   ├── StartCommandHandlerTest.java # Включает тесты deep link активации
│   ├── UserCommandHandlerTest.java
│   ├── BanCommandHandlerTest.java
│   ├── SearchCommandHandlerTest.java    # Тесты поиска с пагинацией
│   ├── CallbackQueryHandlerTest.java
│   ├── TextMessageHandlerTest.java
│   └── StatsCommandHandlerTest.java
├── telegram/messages/
│   └── BotMessageTest.java          # Тесты шаблонов сообщений
└── dto/
    └── DtoTest.java                 # Тесты DTO
```

**Текущее покрытие: 191 тестов**

## 🚢 Развёртывание

### GitLab CI/CD

Проект использует GitLab CI/CD для автоматизации сборки и деплоя. Файл `.gitlab-ci.yml` находится в корне проекта.

**Стадии пайплайна:**

1. **test** - Запуск unit-тестов
2. **build** - Сборка Docker образа
3. **push** - Отправка образа в Docker Registry
4. **deploy** - Развёртывание на сервере

### Необходимые GitLab Variables

| Переменная              | Описание                   |
|-------------------------|----------------------------|
| `DOCKER_HUB_USER`       | Пользователь Docker Hub    |
| `DOCKER_HUB_TOKEN`      | Токен Docker Hub           |
| `DEV_SERVER_HOST`       | Хост сервера для деплоя    |
| `DEV_SERVER_USER`       | SSH пользователь           |
| `SSH_PRIVATE_KEY`       | SSH приватный ключ         |
| `POSTGRES_PASSWORD`     | Пароль PostgreSQL          |
| `REDIS_PASSWORD`        | Пароль Redis               |
| `TELEGRAM_BOT_TOKEN`    | Токен Telegram бота        |
| `TELEGRAM_BOT_USERNAME` | Username бота              |
| `ADMIN_WHITELIST`       | Список Telegram ID админов |

### Ручной деплой

```bash
# На сервере
docker pull your-registry/admin-bot-service:latest

docker stop admin-bot || true
docker rm admin-bot || true

docker run -d \
  --name admin-bot \
  --network social-network-net \
  --restart unless-stopped \
  -e TELEGRAM_BOT_TOKEN=$TELEGRAM_BOT_TOKEN \
  -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
  your-registry/admin-bot-service:latest
```

## 🔗 Интеграция с сервисами

### Account Service (mc-account)

Все запросы идут на internal API:

| Метод   | Endpoint                                  | Описание                |
|---------|-------------------------------------------|-------------------------|
| GET     | `api/v1/internal/account/{id}`            | Получить аккаунт по ID  |
| PUT     | `api/v1/internal/account/block/{id}`      | Заблокировать аккаунт   |
| DELETE  | `api/v1/internal/account/block/{id}`      | Разблокировать аккаунт  |
| GET     | `api/v1/internal/account?page=0&size=10`  | Список аккаунтов        |
| GET     | `api/v1/internal/account/search?email=...`| Поиск по email          |

## 💻 Разработка

### Code Style

- Используйте Lombok: `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`
- Осмысленные имена методов: `getUserById()`, `blockUser()`, `logAction()`
- Не бросайте `Exception`, используйте специфичные `RuntimeException`
- Telegram handlers должны быть "тонкими", бизнес-логика в сервисах
- UUID для ID пользователей, UUID для внутренних ID
- Логируйте важные операции
- `@Transactional` для операций с БД

### Структура коммитов

```
feat: добавлена команда /search
fix: исправлена ошибка парсинга UUID
refactor: оптимизация StatisticsService
test: добавлены тесты для CallbackQueryHandler
docs: обновлён README
```

## 🗺 Roadmap

### v1.0 ✅

- [x] Базовая аутентификация (whitelist)
- [x] Команды управления пользователями
- [x] Статистика платформы
- [x] Аудит логирование
- [x] Inline-клавиатуры
- [x] Unit-тестирование

### v2.1 ✅

- [x] State Machine через Redis
- [x] Поиск пользователей с пагинацией
- [x] Блокировка с выбором причины
- [x] Многоэтапные диалоги
- [x] Команда /cancel для отмены действий

### v2.2 (Текущая) ✅

- [x] Система приглашений для администраторов
- [x] Одноразовые ссылки-приглашения через deep link
- [x] Иерархия ролей (SUPER_ADMIN > ADMIN > SENIOR_MODERATOR > MODERATOR)
- [x] Команда /addadmin для SUPER_ADMIN
- [x] REST API для активации администраторов
- [x] Поле invited_by в таблице admins
- [x] Расширенное тестирование (191 тестов)

### v3.0 (Планируется)

- [ ] Интеграция с Kafka для событий
- [ ] Real-time уведомления
- [ ] Расширенная статистика с графиками
- [ ] Продвинутая аналитика
- [ ] Поддержка нескольких языков
- [ ] Foreign Key Constraint для Admin в AuditLog

## 📄 Лицензия

MIT License - Pet project для изучения микросервисной архитектуры.

## 👥 Авторы

Pet project для социальной сети
