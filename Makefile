# <makefile>
# Objects: db, schema, server, package, env (code environment)
# Actions: clean, build, deploy, test
include makefiles/staging.mk
include makefiles/prerelease.mk
include makefiles/util.mk
include makefiles/externalDB.mk
include makefiles/prod.mk
include makefiles/api-test-data.mk
include makefiles/lfe.mk
include makefiles/gradle.mk

help:
	@IFS=$$'\n' ; \
	help_lines=(`fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//'`); \
	for help_line in $${help_lines[@]}; do \
	    IFS=$$'#' ; \
	    help_split=($$help_line) ; \
	    help_command=`echo $${help_split[0]} | sed -e 's/^ *//' -e 's/ *$$//'` ; \
	    help_info=`echo $${help_split[2]} | sed -e 's/^ *//' -e 's/ *$$//'` ; \
	    printf "%-30s %s\n" $$help_command $$help_info ; \
	done
# </makefile>

define _deploy_schema
	flyway -validateOnMigrate=false -user=openchs -password=password -url=jdbc:postgresql://localhost:5432/$1 -schemas=public -locations=filesystem:./avni-server-api/src/main/resources/db/migration/ -table=schema_version migrate
endef

SU ?= $(shell id -un)
su:=$(SU)
DB=openchs
dbServer=localhost
dbPort=5432

# <postgres>
clean_db_server: _clean_db_server _clean_test_server _drop_roles

_clean_db_server:
	make _clean_db database=$(DB)

_clean_test_server:
	make _clean_db database=openchs_test

_drop_roles:
	-psql -h $(dbServer) -p $(dbPort) -U $(su) -d postgres -c 'drop role openchs';
	-psql -h $(dbServer) -p $(dbPort) -U $(su) -d postgres -c 'drop role demo';
	-psql -h $(dbServer) -p $(dbPort) -U $(su) -d postgres -c 'drop role openchs_impl';
	-psql -h $(dbServer) -p $(dbPort) -U $(su) -d postgres -c 'drop role organisation_user';

_clean_db:
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = '$(database)' AND pid <> pg_backend_pid()"
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres -c 'drop database $(database)';

_build_db:
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres -c "create user openchs with password 'password' createrole";
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres -c 'create database $(database) with owner openchs';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d $(database) -c 'create extension if not exists "uuid-ossp"';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d $(database) -c 'create extension if not exists "ltree"';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d $(database) -c 'create extension if not exists "hstore"';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres  -c 'create role demo with NOINHERIT NOLOGIN';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres  -c 'grant demo to openchs WITH ADMIN OPTION';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres  -c 'create role openchs_impl';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres  -c 'grant openchs_impl to openchs WITH ADMIN OPTION';
	-psql -h $(dbServer) -p $(dbPort) -U ${su} -d postgres  -c 'create role organisation_user createrole admin openchs_impl';
# </postgres>

# <db>
## Drops the database
clean_db: _clean_db_server

build_db: ## Creates new empty database
	make _build_db database=$(DB)

orgId:= $(if $(orgId),$(orgId),0)

delete_org_meta_data:
	psql -h $(dbServer) -p $(dbPort) -U $(su) $(DB) -f avni-server-api/src/main/resources/database/deleteOrgMetadata.sql -v orgId=$(orgId)

delete_org_data:
	@echo 'Delete for Organisation ID = $(orgId)'
	psql -h $(dbServer) -p $(dbPort) -U $(su) $(DB) -f avni-server-api/src/main/resources/database/deleteOrgData.sql -v orgId=$(orgId)

rebuild_db: clean_db build_db ## clean + build db

rebuild_dev_db: rebuild_db deploy_schema

restore_db:
	psql -U openchs $(DB) -f $(sqlfile)

# </db>

# <testdb>
backup_db:
	sudo -u $(su) pg_dump openchs > avni-server-api/target/backup.sql

clean_testdb: ## Drops the test database
	make _clean_db database=openchs_test

_create_demo_organisation:
	-psql -h $(dbServer) -p $(dbPort) -U $(su) -d $(database) -f make-scripts/create_demo_organisation.sql

build_testdb: ## Creates new empty database of test database
	make _build_db database=openchs_test
#	make _create_demo_organisation database=openchs_test

rebuild_testdb: clean_testdb build_testdb ## clean + build test db
# </testdb>


# <schema>
deploy_schema: ## Runs all migrations to create the schema with all the objects
	$(call _deploy_schema,$(DB))

deploy_test_schema: ## Runs all migrations to create the schema with all the objects
	$(call _deploy_schema,openchs_test)
# </schema>


# <server>
start_server: build_server
	OPENCHS_DATABASE=$(DB) AVNI_IDP_TYPE=none java -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

start_server_perf_test_mode: build_server
	OPENCHS_DATABASE=$(DB) AVNI_IDP_TYPE=none java -Xmx1512m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -XX:+UsePerfData -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

start_server_keycloak: build_server
	OPENCHS_MODE=on-premise OPENCHS_DATABASE=$(DB) AVNI_IDP_TYPE=keycloak java -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

check_db_server:
ifndef DBSERVER
	@echo "Provde the DBSERVER variable"
	exit 1
endif

check_db_port:
ifndef DBPORT
	@echo "Provde the DBPORT variable"
	exit 1
endif

start_server_remote_db: check_db_server check_db_port build_server
	AVNI_IDP_TYPE=none OPENCHS_DATABASE_URL=jdbc:postgresql://$(DBSERVER):$(DBPORT)/openchs?currentSchema=public java -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

debug_server: build_server
	AVNI_IDP_TYPE=none OPENCHS_DATABASE=$(DB) java -Xmx2048m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

debug_server_remote_db: build_server
	AVNI_IDP_TYPE=none OPENCHS_DATABASE_URL=jdbc:postgresql://$(DBSERVER):$(DBPORT)/openchs?currentSchema=public java -Xmx2048m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

build_server: ## Builds the jar file
	./gradlew clean build -x test
build-server: build_server

boot_run:
	OPENCHS_DATABASE=$(DB) ./gradlew bootRun

test_server: rebuild_testdb ## Run tests
	GRADLE_OPTS="-Xmx256m" ./gradlew clean test
test-server: test_server

test_server_quick_without_clean_rebuild:  ## Run tests
	MAVEN_OPTS="-Xmx3200m" ./gradlew test

test_server_with_remote_db_quick_with_rebuild:
	make rebuild_testdb su=$(DBUSER) dbServer=$(DBSERVER) dbPort=$(DBPORT)
	OPENCHS_DATABASE_URL=jdbc:postgresql://$(DBSERVER):$(DBPORT)/openchs_test GRADLE_OPTS="-Xmx3200m" ./gradlew clean build test

test_server_with_remote_db:
	make rebuild_testdb su=$(DBUSER) dbServer=$(DBSERVER)
	OPENCHS_DATABASE_URL=jdbc:postgresql://$(DBSERVER):5432/openchs_test GRADLE_OPTS="-Xmx256m" ./gradlew clean test

test_external:
	./gradlew externalTest

start_server_wo_gradle:
	AVNI_IDP_TYPE=none java -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

# LIVE
log_live:
	tail -f /var/log/avni_server/chs.log
# /LIVE

tail-local-log:
	tail -f -n1000 /var/log/avni_server/chs.log

debug_server_live: build_server
	OPENCHS_MODE=live OPENCHS_CLIENT_ID=$(STAGING_APP_CLIENT_ID) OPENCHS_USER_POOL=$(STAGING_USER_POOL_ID) java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar
# <server>

ci-test:
	-psql -h $(dbServer) -p $(dbPort) -Uopenchs openchs_test -c 'create extension if not exists "uuid-ossp"';
	-psql -h $(dbServer) -p $(dbPort) -Uopenchs openchs_test -c 'create extension if not exists "ltree"';
	-psql -h $(dbServer) -p $(dbPort) -Uopenchs openchs_test -c 'create extension if not exists "hstore"';
	make test_server

open_test_results:
	open avni-server-api/build/reports/tests/test/index.html
open-test-results: open_test_results

# <exec-sql>
exec-sql: ## Usage: make exec-sql sqlfile=</path/to/sql>
	psql -Uopenchs -f $(sqlfile)
# </exec-sql>

# remote
tail-prod:
	ssh avni-prod "tail -f /var/log/avni_server/chs.log"

show-dependency-graph:
	./gradlew avni-server-api:dependencies
