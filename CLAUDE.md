# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Avni-server is a Spring Boot 3.3.5 application for health/social data collection and synchronization. It's a multi-module Gradle project (Java 21) with PostgreSQL database, designed for offline-first mobile applications with periodic data sync.

## Build Commands

### Building
```bash
# Build without tests
./gradlew clean build -x test
# Or use Makefile
make build_server

# Create executable JAR
./gradlew clean bootJar
# Output: avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar
```

### Testing
```bash
# Full test suite (rebuilds test database)
make test_server

# Quick test run (no DB rebuild)
./gradlew test

# Run external tests only
./gradlew externalTest

# Open test results in browser
make open_test_results
```

### Running
```bash
# Run with local database
make start_server

# Run with debugging enabled (port 5005)
make debug_server

# Run with Gradle
OPENCHS_DATABASE=openchs ./gradlew bootRun

# Run with remote database
make start_server_remote_db REM_DBSERVER=host REM_DBPORT=5432
```

### Database Management
```bash
# Create fresh database
make rebuild_db

# Run Flyway migrations
make deploy_schema

# Create test database
make rebuild_testdb
```

## Project Structure

### Module Organization
- **avni-server-api**: REST controllers, services, DTOs, mappers, and Flyway migrations
- **avni-server-data**: Domain entities and repositories (shared data layer)
- **avni-rule-server**: JavaScript rule execution engine (GraalVM)

### Key Directories
- `avni-server-api/src/main/java/org/avni/server/`
  - `web/` - REST controllers
  - `service/` - Business logic
  - `mapper/` - DTO ↔ entity mapping
  - `builder/` - Builder pattern implementations
- `avni-server-data/src/main/java/org/avni/server/`
  - `domain/` - JPA entities
  - `dao/` - Repository interfaces
  - `projection/` - Query projections
- `avni-server-api/src/main/resources/`
  - `db/migration/` - Flyway SQL migrations
  - `application.properties` - Configuration

## Architecture

### Request Flow
```
Controller → Service → Repository → Database
     ↓          ↓
   DTO     Mapper (DTO ↔ Entity)
```

### Component Responsibilities
- **Controller**: HTTP endpoint handling, request validation, response formatting
- **Service**: Business logic, transaction management, orchestration
- **Mapper**: Transform between domain objects and DTOs (uses Repository, not Service)
- **Repository**: Database operations via JPA, external service calls (S3, Metabase)
- **DTO**: Three types - Request (input), Response (output), Contract (shared)

### Base Classes
- `CHSEntity`: Audit fields (createdBy, lastModifiedBy, createdDateTime, lastModifiedDateTime)
- `CHSRepository<T>`: Base repository with sync support
- `SyncableRepository<T>`: Sync-specific operations
- `AbstractController<T>`: Common controller functionality

### Multi-tenancy
- Organisation-based isolation via JDBC interceptor
- Flyway sets organisation context per request
- All queries automatically scoped to current organisation

## Data Sync Architecture

### Key Principles
1. **Pagination**: Results ordered by `lastModifiedDateTime ASC, id ASC` to ensure consistency
2. **Concurrent Sync**: Sync window bounded to `(lastModifiedDateTime, now - 10 seconds)` to handle flush delays
3. **Scope-Based**: Data filtered by catchment (geographic) and subject type
4. **Incremental**: Uses `lastModifiedDateTime` to fetch only changed records

### Sync Services
- `ScopeBasedSyncService<T>`: For catchment/subject-type filtered data
- `NonScopeAwareService`: For reference data (no filtering needed)
- `DeviceAwareService`: Device-specific sync handling

### Key Controllers
- `SyncController` - Central sync coordination
- `SyncSubjectController` - Individual subject sync
- `ResetSyncController` - Sync reset operations

## Code Organization Rules

### Where Code Goes
- **Domain & Repository**: Always in avni-server-data module
- **Controller, Service, Mapper, DTO**: Always in avni-server-api module
- **Flyway migrations**: avni-server-api/src/main/resources/db/migration/

### Controller Usage
- Controller can use Service or Repository depending on complexity
- Mapper should use Repository (not Service)
- Service contains all business logic and can use Mapper, Repository, or other Services

### Method Placement
- Always add new methods at the end of the file

### Base Repository Usage
- Use `BaseRepository` and call `getPrisma()` method
- Response classes named `*Response`

## Testing

### Test Types
- `*Test.java` - Unit tests (JUnit 4, Mockito)
- `*IntegrationTest.java` or `*IT.java` - Integration tests (require database)
- `*ET.java` - External tests (external systems like Metabase)

### Running Single Test
```bash
# Run specific test class
./gradlew test --tests "org.avni.server.service.SomeTest"

# Run specific test method
./gradlew test --tests "org.avni.server.service.SomeTest.testMethodName"
```

### Test Database
- Automatically managed by `make test_server`
- Database: `openchs_test`
- Uses `@Sql` annotations for setup/teardown

## Key Technologies

### Core Stack
- Spring Boot 3.3.5
- Spring Data JPA (Hibernate 6.5.2)
- Spring Security (Keycloak or AWS Cognito)
- Spring Batch
- PostgreSQL with extensions: uuid-ossp, ltree, hstore
- Flyway 10.20.1

### Libraries
- **Date/Time**: Joda Time (org.joda.time) - NOT java.time
- **Caching**: EHCache 3.10.8
- **AWS**: S3, Cognito
- **Auth**: Keycloak 24.0.4 or AWS Cognito (JWT)
- **Excel**: Apache POI
- **Validation**: Commons Validator, Passay

### Build
- Gradle with Java 21 toolchain
- Main class: `org.avni.Avni`
- Netflix nebula for OS packaging

## Development Conventions

### Code Style
- **Indentation**: 4 spaces
- **Comments**: Only add when explicitly requested
- **Null checks**: Only add when explicitly requested
- **JavaDocs**: Avoid in favor of self-documenting code

### Database Migrations
- All migrations in `avni-server-api/src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql` (e.g., `V1_99__AddUserTable.sql`)
- Run with `make deploy_schema`

### Authentication
- Two modes: Keycloak (on-premise) or AWS Cognito (cloud)
- Set via `AVNI_IDP_TYPE` environment variable
- JWT-based with role-based authorization

## Common Tasks

### Adding New Entity
1. Create entity in `avni-server-data/src/main/java/org/avni/server/domain/`
2. Extend `CHSEntity` for audit support
3. Create repository in `avni-server-data/src/main/java/org/avni/server/dao/`
4. Extend `CHSRepository` or `SyncableRepository`
5. Add Flyway migration in `avni-server-api/src/main/resources/db/migration/`
6. Create service in `avni-server-api/src/main/java/org/avni/server/service/`
7. Create DTOs and mappers in avni-server-api
8. Create controller in `avni-server-api/src/main/java/org/avni/server/web/`

### Adding API Endpoint
1. Add method to existing controller or create new controller
2. Use `@PreAuthorize` for role-based access control
3. Use request DTO for input, response DTO for output
4. Delegate business logic to service layer
5. Add integration test

### Debugging Sync Issues
1. Check `SyncController` for sync coordination logic
2. Review `SyncParameters` construction
3. Verify repository implements `SyncableRepository`
4. Check service implements `ScopeBasedSyncService` or `NonScopeAwareService`
5. Verify entity extends `CHSEntity` with proper audit fields

## Environment Variables

### Required
- `OPENCHS_DATABASE` - Database name (default: openchs)
- `AVNI_IDP_TYPE` - Identity provider: keycloak, cognito, or none

### Optional
- `OPENCHS_DATABASE_HOST` - Database host (default: localhost)
- `OPENCHS_DATABASE_PORT` - Database port (default: 5432)
- `OPENCHS_MODE` - Deployment mode: live, on-premise
- `OPENCHS_CLIENT_ID` - Cognito client ID (cloud mode)
- `OPENCHS_USER_POOL` - Cognito user pool ID (cloud mode)

## Makefile Targets

Key targets from Makefile:
- `make help` - Show all available targets
- `make rebuild_db` - Clean and rebuild database
- `make build_server` - Build JAR without tests
- `make test_server` - Run full test suite
- `make start_server` - Run server locally
- `make debug_server` - Run with debugger on port 5005
- `make deploy_schema` - Run Flyway migrations

## Repository Patterns

### CHSRepository Methods
```java
public interface CHSRepository<T extends CHSEntity> {
    List<T> findAllByIsVoidedFalse();
    T findByUuid(String uuid);
    T findByName(String name);
    // Plus standard Spring Data JPA methods
}
```

### SyncableRepository Methods
```java
public interface SyncableRepository<T> {
    Slice<T> getSyncResultsAsSlice(...);
    Page<T> getSyncResults(...);
    boolean isEntityChanged(SyncParameters params);
}
```

## Important Files

- `.java-version` - Java version (21)
- `.nvmrc` - Node version for rule-server
- `Makefile` - Build automation
- `ArchitectureDocumentRecord.md` - Sync architecture details
- `.windsurfrules` - Additional coding guidelines
