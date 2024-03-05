open-user-media-lfe:
ifndef orgMedia
	@echo "Provde the orgMedia variable with org media prefix"
	exit 1
endif
	open "https://s3.console.aws.amazon.com/s3/buckets/lfe-user-media?region=ap-south-1&bucketType=general&prefix=teachap/&showversions=false"

open-cognito-lfe:
	open https://ap-south-1.console.aws.amazon.com/cognito/v2/idp/user-pools/ap-south-1_mxlMq9ZIW/users
