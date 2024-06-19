open-user-media-base-prod:
ifndef orgMedia
	@echo "Provde the orgMedia variable with org media prefix"
	exit 1
endif
	open "https://s3.console.aws.amazon.com/s3/buckets/prod-user-media?region=ap-south-1&prefix=$(orgMedia)/&showversions=false"

open-user-media-prod:
ifndef filePath
	@echo "Provde the filePath variable"
	exit 1
endif
	open "https://ap-south-1.console.aws.amazon.com/s3/object/prod-user-media?region=ap-south-1&bucketType=general&prefix=$(filePath)"

open-bulk-uploads-error:
	open "https://s3.console.aws.amazon.com/s3/buckets/prod-user-media?region=ap-south-1&prefix=bulkuploads/error/$(orgMedia)/"

open-cognito-prod:
	open https://ap-south-1.console.aws.amazon.com/cognito/v2/idp/user-pools/ap-south-1_DU27AHJvZ/users

open-thumbnails-folder-prod:
ifndef orgMedia
	@echo "Provde the orgMedia variable with org media prefix"
	exit 1
endif
	open "https://s3.ap-south-1.amazonaws.com/prod-user-media/${orgMedia}/thumbnails/"
