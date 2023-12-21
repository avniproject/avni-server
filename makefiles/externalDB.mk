restore-org-dump:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	make _clean_db _build_db database=avni_org
	psql -U openchs -d avni_org < $(dumpFile)
endif

restore-staging-dump:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	make _clean_db _build_db database=avni_staging
	psql -U openchs -d avni_staging < $(dumpFile)
endif

restore-staging-dump-release-branch:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	make _clean_db _build_db database=avni_staging_released
	psql -U openchs -d avni_staging_released < $(dumpFile)
endif

create-local-db-impl-user: ## Creates new implementation db user in local staging database
ifndef user
	@echo "Provde the variable user"
	exit 1
endif
ifndef db
	@echo "Provde the variable db"
	exit 1
endif
	-psql -U $(su) -d $(db) -c "select create_db_user('$(user)', 'password')"
	-psql -U $(su) -d $(db) -c "select create_implementation_schema('$(user)', '$(user)')"
	-psql -U $(su) -d $(db) -c "grant all privileges on all tables in schema $(user) to $(user)"

create-all-local-staging-db-user: ## Creates all implementation db users in local staging database
	-psql -U $(su) -d avni_staging -c "select create_db_user(db_user, 'password') from organisation where is_voided = false and id <> 1"

create-local-staging-db-user-release-branch:
ifndef user
	@echo "Provde the variable"
	exit 1
else
	-psql -U $(su) -d avni_staging_released -c "select create_db_user('$(user)', 'password')"
endif

run_dump_only:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	psql -U openchs -d avni_org < $(dumpFile)
endif
