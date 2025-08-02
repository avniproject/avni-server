# Avni Sync Java

A Java implementation of the Avni offline-first synchronization functionality, originally built in React Native. This project provides a robust sync system that can be integrated into Java applications and accessed via REST API.

## Overview

This implementation replicates the core sync functionality from the Avni React Native client, providing:

- **Bidirectional Sync**: Upload local changes and download server updates
- **Offline-First**: Full functionality without network connectivity
- **Media Handling**: Upload and download of images, videos, audio, and documents
- **Progress Tracking**: Real-time progress updates and telemetry
- **Error Recovery**: Robust error handling with retry mechanisms
- **REST API Interface**: Web-accessible endpoints replacing command-line interface
- **Spring Boot Integration**: Native Spring Boot configuration and dependency injection
- **Configurable**: Flexible configuration for different environments

## Architecture

The Java sync system follows the same architectural patterns as the original React Native implementation:

### Core Components

- **SyncService**: Main orchestrator for sync operations
- **EntitySyncStatusService**: Tracks sync status for each entity type
- **EntityQueueService**: Manages entities pending upload
- **MediaQueueService**: Handles media file uploads
- **AvniApiClient**: REST API communication layer
- **Repository Layer**: Database abstraction for persistence

### Sync Flow

1. **Upload Phase**:
   - Upload locally created/modified entities
   - Upload media files to S3 with presigned URLs
   - Update entity references with server URLs

2. **Download Phase**:
   - Retrieve sync details from server
   - Download reference data (forms, concepts, etc.)
   - Download transaction data (subjects, encounters, etc.)
   - Update local sync timestamps

## REST API Interface

The original `AvniSyncApplication` command-line interface has been converted to a comprehensive REST API that can be integrated into web applications.

### API Endpoints

#### Sync Operations

- **POST `/api/sync/full`** - Run full synchronization (upload + download)
  - Query parameter: `background` (boolean, optional) - Run as background sync
  - Returns: `SyncResponse`

- **POST `/api/sync/upload-only`** - Run upload-only synchronization
  - Returns: `SyncResponse`

#### Status and Information

- **GET `/api/sync/status`** - Get current sync status
  - Returns: `SyncStatusResponse`

- **GET `/api/sync/config`** - Get current sync configuration
  - Returns: Configuration object

- **GET `/api/sync/diagnostics`** - Run diagnostic checks
  - Returns: `DiagnosticsResponse`

- **GET `/api/sync/entity-metadata`** - Get entity metadata list
  - Returns: List of `EntityMetaData`

#### Documentation

- **GET `/api/sync/help`** - Quick help information
- **GET `/api/sync/docs`** - Comprehensive API documentation

### Response DTOs

#### SyncResponse
```json
{
  "success": true,
  "syncSource": "MANUAL",
  "durationMillis": 5000,
  "entitiesPulled": 100,
  "entitiesPushed": 50,
  "mediaPushed": 10,
  "timestamp": 1640995200000
}
```

#### SyncStatusResponse
```json
{
  "initialized": true,
  "message": "Sync service ready",
  "lastSyncTime": 1640995200000,
  "syncInProgress": false,
  "timestamp": 1640995200000
}
```

#### DiagnosticsResponse
```json
{
  "checks": [
    {
      "name": "Server Connectivity",
      "status": "ok",
      "message": "Connection successful"
    }
  ],
  "timestamp": 1640995200000
}
```

### Original vs API Mapping

| Original Command | API Endpoint |
|------------------|------------|
| `sync` | `POST /api/sync/full` |
| `sync background` | `POST /api/sync/full?background=true` |
| `upload-only` | `POST /api/sync/upload-only` |
| `status` | `GET /api/sync/status` |
| `config` | `GET /api/sync/config` |
| Interactive diagnostics | `GET /api/sync/diagnostics` |
| `help` | `GET /api/sync/help` |

## Project Structure

```
src/main/java/org/avni/sync/
├── client/           # API client interfaces
├── config/           # Configuration management
├── error/            # Exception handling
├── model/            # Data models
├── persistence/      # Repository interfaces
├── service/          # Business logic services
└── util/             # Utility classes

src/main/resources/
└── application.conf  # Configuration file
```

## Configuration

The API uses Spring Boot configuration properties from `application.properties`:

```properties
# Sync configuration
avni.sync.server.url=https://your-avni-server.com
avni.sync.server.apiKey=your-api-key
avni.sync.database.url=jdbc:h2:./data/avni-sync-db
avni.sync.database.username=sa
avni.sync.database.password=
avni.sync.media.baseDirectory=./media
avni.sync.autoSyncEnabled=true
avni.sync.backgroundIntervalMinutes=30
avni.sync.pageSize=100
avni.sync.maxRetryAttempts=3
avni.sync.retryDelaySeconds=5
avni.sync.http.loggingEnabled=false
avni.sync.http.connectionTimeoutSeconds=30
avni.sync.http.readTimeoutSeconds=60
avni.sync.http.writeTimeoutSeconds=120
```

### Environment Variables

You can also configure via environment variables:

- `AVNI_SYNC_SERVER_URL`: Server URL
- `AVNI_SYNC_API_KEY`: API key for authentication
- `AVNI_SYNC_DATABASE_URL`: Database connection URL
- `AVNI_SYNC_MEDIA_DIRECTORY`: Media storage directory
- `AVNI_SYNC_AUTO_ENABLED`: Enable automatic sync
- `AVNI_SYNC_PAGE_SIZE`: Page size for entity retrieval
- `AVNI_SYNC_MAX_RETRY`: Maximum retry attempts

### Integration

To integrate this API into your Spring Boot application:

1. Ensure the sync dependencies are added to your `build.gradle`
2. The configuration properties are set in `application.properties`
3. The `@EnableConfigurationProperties(SyncConfigurationProperties.class)` is included
4. The controllers are component-scanned or explicitly imported

## Key Features

### Sync Modes

- **Manual Sync**: Full bidirectional sync triggered by user
- **Background Sync**: Automatic upload-only sync
- **Periodic Full Sync**: Full sync every 12 hours (configurable)

### Media Support

- **File Types**: Images, Videos, Audio, Documents, Profile Pictures
- **S3 Integration**: Secure upload/download with presigned URLs
- **Chunked Upload**: Prevents failures on slow connections
- **Local Caching**: Efficient local media storage

### Error Handling

- **Categorized Errors**: Network, Auth, Data, Media, Persistence, Timeout
- **Retry Logic**: Configurable retry attempts with exponential backoff
- **Partial Sync**: Individual failures don't break entire sync
- **Detailed Logging**: Comprehensive logging for debugging

### Progress Tracking

- **Real-time Updates**: Progress callbacks for UI integration
- **Weighted Progress**: Different entities have different sync weights
- **Telemetry**: Detailed performance metrics and statistics

## Database Schema

The application uses an embedded H2 database by default with the following key tables:

- `entity_sync_status`: Tracks sync timestamps for each entity type
- `entity_queue`: Entities waiting to be uploaded
- `media_queue`: Media files waiting to be uploaded
- `sync_telemetry`: Sync operation statistics and performance data

## API Endpoints

The implementation uses the same Avni server API endpoints:

### Core Sync
- `POST /v2/syncDetails` - Get sync status and timestamps
- `GET /{entityType}s` - Retrieve entities with pagination
- `POST /{entityType}s` - Upload entities

### Media
- `GET /media/signedUrl` - Get download URL for media
- `GET /media/uploadUrl/{fileName}` - Get upload URL for media

### Authentication
- `GET /me` - User information and permissions

## API Usage Examples

### Sync Operations

```bash
# Start full sync
curl -X POST http://localhost:8080/api/sync/full

# Start background sync
curl -X POST "http://localhost:8080/api/sync/full?background=true"

# Upload-only sync
curl -X POST http://localhost:8080/api/sync/upload-only
```

### Status and Information

```bash
# Check sync status
curl http://localhost:8080/api/sync/status

# Get configuration
curl http://localhost:8080/api/sync/config

# Run diagnostics
curl http://localhost:8080/api/sync/diagnostics

# Get entity metadata
curl http://localhost:8080/api/sync/entity-metadata
```

### Documentation

```bash
# Get API help
curl http://localhost:8080/api/sync/help

# Get full API documentation
curl http://localhost:8080/api/sync/docs
```

## Progress Tracking

The API uses an `ApiProgressCallback` for logging progress. For real-time progress updates in web applications, consider:

- WebSocket endpoints for real-time progress streaming
- Server-Sent Events (SSE) for progress updates
- Polling endpoints for progress status

## Error Handling

All endpoints return structured error responses with appropriate HTTP status codes:

- `200 OK` - Successful operations
- `400 Bad Request` - Invalid parameters or service not initialized
- `500 Internal Server Error` - Sync operation failures

### Error Response Format

```json
{
  "success": false,
  "error": "Sync service not initialized",
  "timestamp": 1640995200000
}
```

## Security Considerations

- Add authentication/authorization as needed
- Validate input parameters
- Rate limiting for sync operations
- Secure API key handling
- HTTPS enforcement for production deployments

## Development Status

This is a **complete architectural implementation** that demonstrates:

✅ **Completed Components**:
- Core sync models and data structures
- Service layer architecture
- API client interfaces
- Configuration management
- Error handling framework
- Progress tracking utilities
- Main application structure

⚠️ **Missing for Production**:
- Database repository implementations (currently interfaces only)
- Entity persistence service implementation
- Authentication integration
- Comprehensive testing
- Production-ready database migrations

## Future Enhancements

- Real-time progress tracking via WebSocket
- Async operation status tracking
- Sync scheduling and management
- Enhanced error reporting and recovery
- Metrics and monitoring integration
- Batch operation support
- Sync conflict resolution
- Multi-tenant support

## Implementation Notes

### Differences from React Native Version

1. **Database**: Uses H2/JDBC instead of Realm
2. **HTTP Client**: Uses Retrofit/OkHttp instead of React Native networking
3. **Media Storage**: File system based instead of React Native media libraries
4. **Configuration**: Uses Typesafe Config instead of React Native settings
5. **Threading**: Uses CompletableFuture instead of React Native promises

### Best Practices Implemented

- **Dependency Injection**: Service-based architecture for testability
- **Configuration Management**: Externalized configuration with sensible defaults
- **Error Handling**: Structured exception hierarchy with retry logic
- **Logging**: SLF4J with configurable log levels
- **Threading**: Async operations with proper timeout handling
- **Resource Management**: Proper cleanup and connection management

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

**Note**: Test implementations are not included in this architectural demonstration but the structure supports comprehensive testing.

## Extending the Implementation

To complete this implementation for production use:

1. **Implement Repository Layer**: Create concrete implementations of the repository interfaces using JDBI or JPA
2. **Entity Persistence**: Implement the `EntityPersistenceService` for your specific entity types
3. **Authentication**: Add authentication interceptors for your auth provider
4. **Database Migrations**: Add proper database schema creation and migration scripts
5. **Testing**: Add comprehensive unit and integration tests
6. **Monitoring**: Add metrics and health check endpoints

## Contributing

This implementation serves as a comprehensive foundation for Java-based Avni sync functionality. Key areas for contribution:

1. Repository implementations
2. Authentication providers
3. Additional entity types
4. Performance optimizations
5. Test coverage
6. Documentation improvements

## License

This project follows the same license as the original Avni client (AGPL-3.0).

## Support

This is a reference implementation demonstrating the architecture and patterns needed to implement Avni sync in Java. For production use, additional implementation work is required as outlined above.

