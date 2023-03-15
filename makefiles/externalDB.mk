restore_staging_dump:
ifndef dumpFile
	@echo "Provde the dumpFile variable"
	exit 1
else
	make _clean_db _build_db database=avni_staging
	psql -U openchs -d avni_staging < $(dumpFile)
endif

create_local_staging_db_user:
ifndef user
	@echo "Provde the variable"
	exit 1
else
	-psql -U $(su) -d avni_staging -c "select create_db_user('$(user)', 'password')"
endif
