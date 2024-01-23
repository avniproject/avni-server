create-base-local-test-data-only:
	@echo 'Creating base data'
	psql -h $(dbServer) -p $(dbPort) -U $(su) $(DB) -f avni-server-api/src/main/resources/database/createBaseLocalTestData.sql

run-api-test-data-only:
	newman run postman/local_test_data_setup.json -e postman/localhost.postman_environment.json

create-local-test-data: create-base-local-test-data-only run-api-test-data-only
recreate-local-test-data: rebuild_db deploy_schema create-local-test-data
recreate-local-base-test-data: rebuild_db deploy_schema create-base-local-test-data-only
