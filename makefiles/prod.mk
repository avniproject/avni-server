open-user-media-prod:
ifndef orgMedia
	@echo "Provde the orgMedia variable with org media prefix"
	exit 1
endif
	open "https://s3.console.aws.amazon.com/s3/buckets/prod-user-media?region=ap-south-1&prefix=$(orgMedia)/&showversions=false"
