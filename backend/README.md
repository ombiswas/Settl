# Settl — Backend

Spring Boot 3 REST API for **Settl**, an expense-splitting and debt-simplification platform.

## Requirements
- Java 21+
- Maven 3.9+ (or use `./mvnw`)
- Docker & Docker Compose (for local PostgreSQL 16 + Redis 7)

## Quick Start

### 1. Start Database & Cache Containers
From the root directory:
```bash
docker compose up -d
```
This starts:
- **PostgreSQL 16**: Port `5432` (database: `expense_splitter`, user: `postgres`, password: `changeme`)
- **Redis 7**: Port `6379`

### 2. Environment Configuration
Copy the `.env.example` to `.env` inside the `backend/` directory:
```bash
cp .env.example .env
```

### 3. Run the Backend
Using Maven Wrapper:
```bash
./mvnw spring-boot:run
```
(On Windows PowerShell: `.\mvnw.cmd spring-boot:run`)

### 4. Verify
- **Health Check**: `GET http://localhost:8080/api/health`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

## Run Tests
```bash
./mvnw clean test
```
