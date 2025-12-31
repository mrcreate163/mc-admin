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
├── domain/          # Модели State Machine
│   ├── BotState.java           # Перечисление состояний
│   ├── ConversationState.java  # Модель состояния диалога
│   └── StateDataKey.java       # Константы ключей данных
├── telegram/        # Telegram бот и обработчики
│   ├── handler/     # Обработчики команд
│   │   ├── base/    # Базовые классы handlers
│   │   │   ├── BaseCommandHandler.java
│   │   │   ├── StatefulCommandHandler.java
│   │   │   └── StatelessCommandHandler.java
│   │   ├── StartCommandHandler.java
│   │   ├── UserCommandHandler.java
│   │   ├── BanCommandHandler.java
│   │   ├── SearchCommandHandler.java
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
│   ├── ConversationStateService.java   # Управление состояниями в Redis
│   └── StateTransitionService.java     # Валидация переходов
├── repository/      # Слой доступа к данным
├── entity/          # JPA сущности
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
| `AWAITING_ADMIN_TELEGRAM_ID` | Ожидание Telegram ID для добавления админа (v3.0) |
| `AWAITING_ADMIN_ROLE` | Ожидание выбора роли для нового админа (v3.0) |
| `CONFIRMING_ADMIN_CREATION` | Подтверждение создания нового админа (v3.0) |

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

## ⭐ Возможности (v2.0)

### Telegram команды

| Команда | Описание |
|---------|----------|
| `/start` | Приветственное сообщение и главное меню |
| `/user <id>` | Просмотр информации о пользователе |
| `/ban <id>` | Заблокировать пользователя (с выбором причины) |
| `/unban <id>` | Разблокировать пользователя |
| `/search [query]` | Поиск пользователей по email |
| `/stats` | Статистика платформы |
| `/cancel` | Отменить текущее действие |

### Функции администратора

- ✅ **Управление пользователями**: просмотр, блокировка/разблокировка
- ✅ **Поиск пользователей**: поиск по email с пагинацией
- ✅ **Блокировка с причиной**: выбор причины из предустановленных или ввод своей
- ✅ **State Machine**: многоэтапные диалоги с хранением состояния в Redis
- ✅ **Статистика**: общее количество, новые за сегодня, заблокированные
- ✅ **Аудит**: все действия администраторов логируются в БД
- ✅ **Inline-клавиатуры**: интерактивные кнопки для быстрых действий
- ✅ **Авторизация**: доступ на основе whitelist Telegram ID

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

| Колонка            | Тип          | Описание                             |
|--------------------|--------------|--------------------------------------|
| `telegram_user_id` | BIGINT       | Telegram ID(primary key)             |
| `username`         | VARCHAR(255) | Telegram username                    |
| `first_name`       | VARCHAR(255) | Имя                                  |
| `role`             | VARCHAR(50)  | Роль (SUPER_ADMIN, ADMIN, MODERATOR) |
| `is_active`        | BOOLEAN      | Активен                              |
| `created_at`       | TIMESTAMP    | Дата создания                        |
| `updated_at`       | TIMESTAMP    | Дата обновления                      |

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
    ├── 001-create-admins-table.yaml
    └── 002-create-audit-log-table.yaml
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
│   ├── ConversationStateServiceTest.java  # Тесты State Machine сервиса
│   └── StateTransitionServiceTest.java   # Тесты валидации переходов
├── telegram/handler/
│   ├── StartCommandHandlerTest.java
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

**Текущее покрытие: 158 тестов**

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

### v2.1 (Текущая) ✅

- [x] State Machine через Redis
- [x] Поиск пользователей с пагинацией
- [x] Блокировка с выбором причины
- [x] Многоэтапные диалоги
- [x] Команда /cancel для отмены действий
- [x] Расширенное тестирование (158 тестов)

### v2.2
- [ ] Управление администраторами через БД
- [ ] Расширенная статистика с графиками
- [ ] Дополнительные функции модерации
- [ ] Foreign Key Constraint для Admin в AuditLog

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
