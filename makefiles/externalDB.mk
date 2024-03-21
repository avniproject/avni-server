restore-dump-only:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	psql -U openchs -d avni_org < $(dumpFile)
endif

restore-org-dump:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	sed -i '' 's/from form/from public.form/g' "$(dumpFile)"
	sed -i '' 's/inner join form/inner join public.form/g' "$(dumpFile)"
	make _clean_db _build_db database=avni_org
	make restore-dump-only dumpFile=$(dumpFile)
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

run-dump-only:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	psql -U openchs -d avni_org < $(dumpFile)
endif

tunnel-db:
ifndef host
	@echo "Provde the host variable"
	exit 1
endif
ifndef dbServer
	@echo "Provde the hostName variable"
	exit 1
endif
	ssh $(host) -L 5433:$(dbServer):5432

tunnel-prerelease-db:
	make tunnel-db host=avni-prerelease dbServer=prereleasedb.avniproject.org
tunnel-prod-read-db:
	make tunnel-db host=avni-prod dbServer=serverdb.read.openchs.org
tunnel-prod-db:
	make tunnel-db host=avni-prod dbServer=serverdb.openchs.org
tunnel-lfe-prod-db:
	make tunnel-db host=avni-lfe-prod dbServer=prod-db2.cdsbgtdqfjhs.ap-south-1.rds.amazonaws.com
tunnel-staging-db:
	make tunnel-db host=avni-staging dbServer=stagingdb.openchs.org
tunnel-rwb-prod:
	make tunnel-db host=rwb-prod dbServer=serverdb.rwb.avniproject.org

dump-org-data-prerelease:
	make dump-org-data dbRole=$(dbRole) prefix=prerelease
dump-org-data-prerelease-with-copy:
	make dump-org-data-with-copy dbRole=$(dbRole) prefix=prerelease
dump-org-data-prod:
	make dump-org-data dbRole=$(dbRole) prefix=prod
dump-org-data-prod-with-copy:
	make dump-org-data-with-copy dbRole=$(dbRole) prefix=prod
dump-org-data-staging:
	make dump-org-data dbRole=$(dbRole) prefix=staging

dump-org-data-with-copy:
ifndef dbRole
	@echo "Provde the dbRole variable"
	exit 1
endif
	pg_dump -h localhost -p 5433 \
		--dbname=openchs \
		--username=openchs \
		--role=$(dbRole) \
		--file=$(HOME)/projects/avni/avni-db-dumps/$(prefix)-$(dbRole).sql \
		--enable-row-security --verbose --schema=public --host=localhost \
		--exclude-table-data=audit \
		--exclude-table-data='public.sync_telemetry' \
		--exclude-table-data='rule_failure_log' \
		--exclude-table-data='scheduled_job_run' \
		--exclude-table-data='qrtz_*' \
		--exclude-table-data='batch_*' \

dump-org-data:
ifndef dbRole
	@echo "Provde the dbRole variable"
	exit 1
endif
	pg_dump -h localhost -p 5433 \
		--dbname=openchs \
		--username=openchs \
		--role=$(dbRole) \
		--file=$(HOME)/projects/avni/avni-db-dumps/$(prefix)-$(dbRole).sql \
		--enable-row-security --verbose --schema=public --host=localhost \
		--exclude-table-data=audit \
		--exclude-table-data='public.sync_telemetry' \
		--exclude-table-data='rule_failure_log' \
		--exclude-table-data='scheduled_job_run' \
		--exclude-table-data='qrtz_*' \
		--exclude-table-data='batch_*' \
		--exclude-table='public.individual_copy' \
		--exclude-table='public.program_enrolment_copy' \
		--exclude-table='public.encounter_copy' \
		--exclude-table='public.program_encounter_copy' \
		--exclude-table='public.individual_copy_ck' \
		--exclude-table='public.program_enrolment_ck' \
		--exclude-table='public.encounter_ck' \
		--exclude-table='public.program_encounter_ck' \
		--exclude-table='public.individual_copy_ihmp' \
		--exclude-table='public.program_enrolment_ihmp' \
		--exclude-table-data='public.individual_02_24' \
		--exclude-table-data='public.program_enrolment_02_24'
