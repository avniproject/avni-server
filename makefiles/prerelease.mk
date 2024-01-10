tunnel_prerelease_db:
	ssh avni-prerelease -L 6015:prereleasedb.openchs.org:5432

open-user-media-prerelease:
ifndef orgMedia
	@echo "Provde the orgMedia variable with org media prefix"
	exit 1
endif
	open "https://s3.console.aws.amazon.com/s3/buckets/prerelease-user-media?region=ap-south-1&prefix=$(orgMedia)/&showversions=false"

open-cognito-prerelease:
	open https://ap-south-1.console.aws.amazon.com/cognito/v2/idp/user-pools/ap-south-1_vfNvMvMk9/users?region=ap-south-1
