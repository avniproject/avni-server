# Avni Server Windsurf Rules
version = 1.0.0

# Tech Stack
- it uses JPA, Spring Security, Spring Batch, and Spring Rest
- it connects to a Postgres database
- flyway is used for database migrations

# Language usage
- Do not add comments and null checks unless asked
- Check Java version from .java_version
- always add new methods at the end of the file

# Code organization
- Repository and domain code goes in avni-serer-data module.
- Service, controller, mapper, and dto code goes in avni-server-api module. Flyway migrations are in this module.

# Building blocks (or where to put which type of code)
- There are following building blocks for request processing - Controller, Service, Mapper, Repository, and DTO.
- Batch jobs use Tasklet/Job - which inturn should use Writer or Service
- DTOs are of three types - Request, Response, and Contract.
- Repository should have only basic database operations directly using JPA for single entity type usually. Repository should be responsible for calling external services like Metabase, S3, etc.
- Service should have all the business logic. It can have mapper, repository, or other services
- Mapper should map between domain objects and DTO objects and vice versa
- take request in a typed dto and return response in a typed dto
- use BaseRepository always and getPrisma method from it
- the web response contract classes are called Response
- Mapper should use Repository and not Service
- Controller can use Service or Repository depending on the complexity of the operation


#----------------------------------------------
# PROJECT INFORMATION
#----------------------------------------------
project.name = avni-server
project.type = java
project.framework = spring-boot

#----------------------------------------------
# PROJECT STRUCTURE
#----------------------------------------------
structure.sourceRoot = avni-server-api/src/main/java
structure.resourceRoot = avni-server-api/src/main/resources
structure.testRoot = avni-server-api/src/test
structure.packagePrefix = org.avni

# Modules
modules = avni-server-api, avni-server-data, avni-rule-server

# Module Organization
organization.data = repository and domain code in avni-server-data module
organization.api = service, controller, mapper, dto code and flyway migrations in avni-server-api module

#----------------------------------------------
# BUILD CONFIGURATION
#----------------------------------------------
build.tool = gradle
build.mainClass = org.avni.Avni
build.javaVersion = 21
build.command.build = ./gradlew clean bootJar
build.command.test = ./gradlew test
build.command.run = java -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

#----------------------------------------------
# TECH STACK AND DEPENDENCIES
#----------------------------------------------
# Core Technologies
tech.frameworks = JPA, Spring Security, Spring Batch, Spring Rest
tech.database = postgresql
tech.migrations = flyway

# Basic Dependencies
dependencies.database = postgresql
dependencies.cache = ehcache
dependencies.security = spring-security

# API Dependencies
api.dependencies.spring-boot-version = 3.3.5
api.dependencies.spring-boot-web = org.springframework.boot:spring-boot-starter-web
api.dependencies.spring-boot-security = org.springframework.boot:spring-boot-starter-security
api.dependencies.spring-boot-data-jpa = org.springframework.boot:spring-boot-starter-data-jpa
api.dependencies.spring-boot-data-rest = org.springframework.boot:spring-boot-starter-data-rest
api.dependencies.spring-boot-batch = org.springframework.boot:spring-boot-starter-batch
api.dependencies.keycloak = org.keycloak:keycloak-spring-boot-starter:24.0.4
api.dependencies.aws-s3 = com.amazonaws:aws-java-sdk-s3
api.dependencies.aws-cognito = com.amazonaws:aws-java-sdk-cognitoidp
api.dependencies.jackson = com.fasterxml.jackson.core:jackson-databind:2.15.4

# Library Usage
library.datetime = Use Joda Time (org.joda.time) instead of java.time for all date and time handling, Maintain consistent date formatting patterns across the codebase

#----------------------------------------------
# CODE STYLE AND CONVENTIONS
#----------------------------------------------
# General Code Style
codeStyle.indentation = spaces
codeStyle.indentSize = 4
codeStyle.lineLength = 120
codeStyle.packageNaming = org.avni.{module}.{component}

# Java-specific Code Style
codestyle.java.braces = same_line
codestyle.java.imports_order = java,javax,org,com
codestyle.java.static_imports = bottom
codestyle.java.wildcard_imports = false
codestyle.java.final_parameters = false
codestyle.java.final_locals = when_needed

# Coding Conventions
coding.comments = add only when explicitly requested
coding.nullchecks = add only when explicitly requested
coding.javaversion.check = .java_version
coding.methodplacement = add new methods at end of file

# Documentation Guidelines
documentation.principles = Avoid JavaDocs in favor of self-documenting code, Use clear method and variable names that explain their purpose, Only add inline comments for complex logic that cannot be made clear through refactoring, Do not document what the code does (it should be obvious), Document why certain decisions were made when not obvious

# Refactoring Guidelines
refactoring.documentation = Avoid JavaDocs, coding style preferred is single-line format, rely on self-documenting code and clear naming instead

#----------------------------------------------
# ARCHITECTURE AND DESIGN PATTERNS
#----------------------------------------------
# Component Structure
components.request = Controller, Service, Mapper, Repository, DTO
components.batch = Tasklet/Job using Writer or Service
components.dto.types = Request, Response, Contract
components.repository = basic database operations using JPA, external service calls (Metabase, S3)
components.service = business logic, can use mapper, repository, or other services
components.mapper = map between domain objects and DTO objects
components.api = typed request dto and typed response dto
components.repository.base = BaseRepository, use getPrisma method
components.response = web response contract classes named Response

# Code Patterns
patterns.controllers = **/*Controller.java
patterns.services = **/*Service.java
patterns.repositories = **/*Repository.java
patterns.entities = org/avni/server/domain/**/*.java

# API Architecture
api.pattern.controllers = **/*Controller.java
api.pattern.repositories = **/*Repository.java
api.pattern.services = **/*Service.java
api.pattern.domain = org/avni/server/domain/**/*.java
api.pattern.framework = org/avni/server/framework/**/*.java

# API REST
api.rest.hateoas = true
api.rest.model-processors = true
api.rest.base-path = /api

# API Build
api.build.jar-name = avni-server
api.build.jar-version = 0.0.1-SNAPSHOT
api.build.main-class = org.avni.Avni
api.build.custom-tasks = externalTest

# API Documentation
api.docs.format = spring-data-rest
api.docs.base-path = /api

# API Messaging
api.messaging.enabled = true
api.messaging.provider = glific
api.messaging.package = org.avni.messaging

#----------------------------------------------
# ARCHITECTURAL PRINCIPLES
#----------------------------------------------
# Code Structure and Modularity
modularity.principles = Create small, focused methods with a single responsibility, Limit method size to improve readability and testability, Extract complex logic into separate utility classes, Use immutable objects wherever possible, Avoid deep nesting of methods and control structures

# Service Layer Principles
service.principles = Follow the Single Responsibility Principle, Inject dependencies rather than creating them, Use interfaces for service contracts, Keep business logic in service layer not controllers, Validate input parameters at service boundaries

# Repository Layer Principles
repository.principles = Use Spring Data interfaces where possible, Create custom queries with JPQL or native SQL when needed, Avoid N+1 query problems, Use pagination for large result sets, Consider using query projections for performance

# Controller Layer Principles
controller.principles = Keep controllers thin, Delegate business logic to services, Use DTOs for request/response objects, Implement proper error handling, Document APIs with OpenAPI/Swagger

# Domain Model Guidelines
domain.principles = Use rich domain models, Encapsulate business rules within domain objects, Validate domain invariants, Use value objects for immutable concepts, Consider using domain events for cross-aggregate communication

#----------------------------------------------
# TESTING
#----------------------------------------------
# Testing Patterns
testing.unitTestPattern = **/*Test.java
testing.integrationTestPattern = **/*IT.java
testing.coverageThreshold = 70

# Testing Principles
testing.principles = Write tests for all new code, Follow the Arrange-Act-Assert pattern, Use meaningful test names that describe behavior, Mock external dependencies, Test edge cases and error conditions

# Testing Frameworks
testing.framework.unit = junit4
testing.framework.assertions = assertj
testing.framework.mocking = mockito
testing.framework.spring = spring-boot-test

# Test Patterns
testing.pattern.baseControllerTest = AbstractControllerIntegrationTest
testing.pattern.testBuilders = true
testing.pattern.dataJpaTest = true

# Test Naming Conventions
testing.naming.unitTests = *Test.java
testing.naming.integrationTests = *IT.java,*IntegrationTest.java
testing.naming.entityTests = *ET.java

# Test Structure
testing.modules.api = avni-server-api/src/test
testing.modules.data = avni-server-data/src/test
testing.packagePrefix = org.avni

# Test Dependencies
testing.dependencies.junit = junit:junit:4.13.2
testing.dependencies.mockito = org.mockito:mockito-core:4.8.0
testing.dependencies.assertj = org.assertj:assertj-core:3.23.1
testing.dependencies.springTest = org.springframework.boot:spring-boot-starter-test

#----------------------------------------------
# INFRASTRUCTURE
#----------------------------------------------
# Database
database.migrations.tool = flyway
database.migrations.location = avni-server-api/src/main/resources/db/migration/

# API Caching
api.cache.provider = ehcache
api.cache.implementation = org.ehcache:ehcache:3.10.8:jakarta
api.cache.hibernate = org.hibernate.orm:hibernate-jcache:6.5.2.Final

# Security
security.authentication = keycloak Or cognito
security.authorization = role-based

# API Security
api.security.provider = keycloak or cognito
api.security.jwt = true
api.security.role-based = true

# Logging
logging.framework = logback
logging.configFile = avni-server-api/src/main/resources/logback-spring.xml

#----------------------------------------------
# DEPLOYMENT
#----------------------------------------------
deployment.containerization = docker
deployment.baseImage = amazoncorretto:21
deployment.port = 8080
deployment.healthCheckPath = /actuator/health
deployment.memory = 2Gi
deployment.cpu = 1