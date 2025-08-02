# Avni Camp Sync Deployment Guide

This guide explains how to deploy the Avni sync functionality for camp operations.

## Overview

The implementation consists of:
1. **avni-sync backend** - PostgreSQL-based sync service integrated into avni-server
2. **avni-camp-webapp** - React web application for camp users
3. **PostgreSQL database** - Shared between local avni-server and sync functionality

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  Avni Cloud     │    │   Camp Van       │    │ Field Workers   │
│  (Remote)       │    │  (Local Server)  │    │ (Web Browsers)  │
│                 │    │                  │    │                 │
│ - Avni Server   │◄──►│ - Avni Server    │◄──►│ - Avni Webapp   │
│ - PostgreSQL DB │    │ - PostgreSQL DB  │    │ - Camp Webapp   │
│ - User Data     │    │ - Sync Service   │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
        │ Internet              │ Sync                   │ Local Network
        └───────────────────────┘                       │
                                                        │
                        When internet available,       │
                        sync pushes/pulls data         │
```

## Components Implemented

### 1. PostgreSQL EntityPersistenceService
- **File**: `org.avni.sync.persistence.PostgreSQLEntityPersistenceService`
- **Purpose**: Handles persistence and retrieval of entities from PostgreSQL
- **Features**:
  - CRUD operations for all Avni entities
  - JSON-based entity storage
  - Automatic schema initialization
  - Entity table mapping for Avni data model

### 2. Sync Status Tracking
- **File**: `org.avni.sync.persistence.PostgreSQLEntitySyncStatusRepository`
- **Purpose**: Tracks sync timestamps and status for each entity type
- **Tables**: `entity_sync_status`

### 3. Sync Telemetry
- **File**: `org.avni.sync.persistence.PostgreSQLSyncTelemetryRepository`
- **Purpose**: Records sync operation statistics and performance data
- **Tables**: `sync_telemetry`

### 4. Entity and Media Queues
- **Files**: 
  - `org.avni.sync.service.PostgreSQLEntityQueueService`
  - `org.avni.sync.service.PostgreSQLMediaQueueService`
- **Purpose**: Manages entities and media files pending upload
- **Tables**: `entity_queue`, `media_queue`

### 5. Enhanced Sync Controller
- **File**: `org.avni.sync.web.SyncController`
- **Purpose**: REST API endpoints for sync operations
- **Endpoints**:
  - `POST /api/sync/full` - Full dual sync
  - `POST /api/sync/upload-only` - Upload only
  - `GET /api/sync/status` - Current sync status
  - `GET /api/sync/history` - Sync history
  - `GET /api/sync/statistics` - Sync statistics
  - `POST /api/sync/cancel` - Cancel current sync

### 6. Camp Webapp Integration
- **File**: `avni-camp-webapp/src/components/Dashboard.jsx`
- **Purpose**: Triggers sync operations via REST API
- **Features**:
  - Real-time sync progress display
  - Sync status monitoring
  - Error handling and logging

## Deployment Steps

### 1. Database Setup

The sync functionality creates its own tables automatically, but ensure your PostgreSQL database is properly configured:

```sql
-- Ensure the database exists
CREATE DATABASE avni;

-- Grant permissions to the avni user
GRANT ALL PRIVILEGES ON DATABASE avni TO avni;
```

### 2. Configuration

Update `application.properties` or create `application-sync.properties`:

```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/avni
spring.datasource.username=avni
spring.datasource.password=your-password

# Sync Configuration
avni.sync.server.url=https://your-cloud-avni-server.com
avni.sync.server.apiKey=your-api-key
avni.sync.device.id=camp-van-001

# Enable sync profile
spring.profiles.active=sync

# CORS for camp webapp
spring.web.cors.allowed-origins=http://localhost:3000,http://localhost:5173
```

### 3. Build and Deploy

Build the avni-server with sync functionality:

```bash
cd avni-server
./gradlew build
java -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=sync
```

### 4. Deploy Camp Webapp

```bash
cd avni-camp-webapp
npm install
npm run build
# Deploy build/ directory to your web server
```

### 5. Initialize Sync

On first startup, the sync service will automatically:
1. Create necessary database tables
2. Initialize entity sync status records
3. Set up queue tables

## Usage

### From Camp Webapp

1. Open the camp webapp in your browser
2. Navigate to the sync control panel
3. Click "Start Sync" to trigger dual sync
4. Monitor progress and status in real-time

### Via REST API

```bash
# Start full sync
curl -X POST http://localhost:8080/api/sync/full

# Check status
curl http://localhost:8080/api/sync/status

# View sync history
curl http://localhost:8080/api/sync/history

# Get statistics
curl http://localhost:8080/api/sync/statistics
```

## Sync Process

### 1. Pull Phase (Download from Cloud)
- Downloads reference data (forms, concepts, etc.)
- Downloads transaction data (subjects, encounters, etc.)
- Updates local PostgreSQL database
- Records sync timestamps

### 2. Push Phase (Upload to Cloud)
- Uploads locally created/modified entities
- Uploads media files
- Updates entity references with server URLs
- Removes successfully uploaded items from queues

### 3. Status Tracking
- Records sync start/end times
- Tracks entities pushed/pulled counts
- Records any errors or failures
- Stores performance metrics

## Entity Mapping

The sync service maps Avni entities to PostgreSQL tables:

| Entity Name | PostgreSQL Table |
|-------------|------------------|
| Individual | individual |
| ProgramEnrolment | program_enrolment |
| ProgramEncounter | program_encounter |
| Encounter | encounter |
| Checklist | checklist |
| ChecklistItem | checklist_item |
| GroupSubject | group_subject |
| IndividualRelationship | individual_relationship |
| AddressLevel | address_level |
| Concept | concept |
| SubjectType | subject_type |
| Program | program |
| EncounterType | encounter_type |
| Form | form |
| FormMapping | form_mapping |

## Monitoring and Troubleshooting

### Logs

Check application logs for sync operations:

```bash
tail -f logs/application.log | grep "org.avni.sync"
```

### Database Queries

Monitor sync status:

```sql
-- Check sync status
SELECT * FROM entity_sync_status ORDER BY last_modified_date_time DESC;

-- Check recent sync operations
SELECT * FROM sync_telemetry ORDER BY sync_start_time DESC LIMIT 10;

-- Check pending uploads
SELECT entity_name, COUNT(*) FROM entity_queue WHERE NOT processed GROUP BY entity_name;

-- Check pending media uploads
SELECT COUNT(*) FROM media_queue WHERE NOT uploaded;
```

### Common Issues

1. **Sync fails with authentication error**
   - Check API key configuration
   - Verify server URL is correct
   - Ensure internet connectivity

2. **Database connection errors**
   - Verify PostgreSQL is running
   - Check connection string and credentials
   - Ensure database exists

3. **Camp webapp can't connect to sync API**
   - Check CORS configuration
   - Verify avni-server is running
   - Check firewall settings

## Configuration Reference

### Complete Configuration Example

```properties
# Server Configuration
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/avni
spring.datasource.username=avni
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Sync Configuration
avni.sync.server.url=https://app.avniproject.org
avni.sync.server.apiKey=your-api-key-here
avni.sync.device.id=camp-van-001
avni.sync.media.baseDirectory=./media
avni.sync.autoSyncEnabled=true
avni.sync.backgroundIntervalMinutes=30
avni.sync.pageSize=100
avni.sync.maxRetryAttempts=3
avni.sync.retryDelaySeconds=5

# HTTP Configuration
avni.sync.http.loggingEnabled=true
avni.sync.http.connectionTimeoutSeconds=30
avni.sync.http.readTimeoutSeconds=60
avni.sync.http.writeTimeoutSeconds=120

# CORS Configuration
spring.web.cors.allowed-origins=http://localhost:3000,http://localhost:5173
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*
spring.web.cors.allow-credentials=true

# Logging
logging.level.org.avni.sync=DEBUG
logging.level.org.springframework.jdbc=INFO
logging.level.org.springframework.web=INFO
```

## Security Considerations

1. **API Key Security**: Store API keys securely, use environment variables in production
2. **Database Security**: Use strong passwords, enable SSL for database connections
3. **Network Security**: Configure firewalls appropriately, use HTTPS in production
4. **CORS Configuration**: Restrict CORS origins to known camp webapp URLs

## Backup and Recovery

### Database Backup

```bash
# Create backup
pg_dump -h localhost -U avni avni > backup_$(date +%Y%m%d_%H%M%S).sql

# Restore backup
psql -h localhost -U avni avni < backup_20240802_120000.sql
```

### Sync Data Recovery

If sync data is corrupted:

1. Stop the avni-server
2. Clear sync tables:
   ```sql
   TRUNCATE entity_sync_status, sync_telemetry, entity_queue, media_queue;
   ```
3. Restart avni-server (tables will be recreated)
4. Run full sync to re-download all data

## Performance Optimization

1. **Database Indexing**: Ensure proper indexes on sync tables
2. **Page Size**: Adjust `avni.sync.pageSize` based on network conditions
3. **Retry Configuration**: Tune retry attempts and delays for your environment
4. **Media Storage**: Use adequate disk space for media files
5. **Memory Settings**: Allocate sufficient JVM heap for large sync operations

## Production Deployment Checklist

- [ ] PostgreSQL database properly configured
- [ ] API keys and credentials secured
- [ ] CORS properly configured for production URLs
- [ ] HTTPS enabled
- [ ] Firewall rules configured
- [ ] Backup strategy implemented
- [ ] Monitoring and alerting set up
- [ ] Log rotation configured
- [ ] Performance settings tuned
- [ ] Camp webapp deployed and accessible
- [ ] Initial sync tested successfully