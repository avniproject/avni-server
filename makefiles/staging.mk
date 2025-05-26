# I have setup the environment variables in my bash_profile so that I can just run it whenever I want in live mode. You could do that too (Vivek).
tunnel_staging_server_for_debug:
	ssh avni-staging -L 5005:127.0.0.1:5005

start_server_staging: build_server
	-mkdir -p /tmp/openchs && sudo ln -s /tmp/openchs /var/log/avni_server

	AVNI_IDP_TYPE=cognito \
	OPENCHS_CLIENT_ID=$(OPENCHS_STAGING_APP_CLIENT_ID) \
	OPENCHS_USER_POOL=$(OPENCHS_STAGING_USER_POOL_ID) \
	OPENCHS_IAM_USER=$(OPENCHS_STAGING_IAM_USER) \
	OPENCHS_IAM_USER_ACCESS_KEY=$(OPENCHS_STAGING_IAM_USER_ACCESS_KEY) \
	OPENCHS_IAM_USER_SECRET_ACCESS_KEY=$(OPENCHS_STAGING_IAM_USER_SECRET_ACCESS_KEY) \
	OPENCHS_BUCKET_NAME=staging-user-media \
  	OPENCHS_DATABASE_HOST=localhost OPENCHS_DATABASE_PORT=5433 \
    	java -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

debug_server_staging: build_server
	-mkdir -p /tmp/openchs && sudo ln -s /tmp/openchs /var/log/avni_server
	AVNI_IDP_TYPE=cognito \
	OPENCHS_CLIENT_ID=$(OPENCHS_STAGING_APP_CLIENT_ID) \
	OPENCHS_USER_POOL=$(OPENCHS_STAGING_USER_POOL_ID) \
	OPENCHS_IAM_USER=$(OPENCHS_STAGING_IAM_USER) \
	OPENCHS_IAM_USER_ACCESS_KEY=$(OPENCHS_STAGING_IAM_USER_ACCESS_KEY) \
	OPENCHS_IAM_USER_SECRET_ACCESS_KEY=$(OPENCHS_STAGING_IAM_USER_SECRET_ACCESS_KEY) \
	OPENCHS_BUCKET_NAME=staging-user-media \
	OPENCHS_DATABASE_HOST=localhost OPENCHS_DATABASE_PORT=5433 \
		java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar


debug_server_staging_s3_without_idp: build_server
	AVNI_IDP_TYPE=none OPENCHS_DATABASE=$(DB) OPENCHS_IAM_USER=$(OPENCHS_STAGING_IAM_USER) \
	OPENCHS_IAM_USER_ACCESS_KEY=$(OPENCHS_STAGING_IAM_USER_ACCESS_KEY) \
	OPENCHS_IAM_USER_SECRET_ACCESS_KEY=$(OPENCHS_STAGING_IAM_USER_SECRET_ACCESS_KEY) \
	OPENCHS_S3_IN_DEV=true \
	OPENCHS_BUCKET_NAME=staging-user-media \
	java -Xmx2048m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -jar avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar

tail-staging-log:
	ssh avni-staging "tail -f -n1000 /var/log/avni_server/chs.log"

open-user-media-staging:
ifndef orgMedia
	@echo "Provde the orgMedia variable with org media prefix"
	exit 1
endif
	open "https://s3.console.aws.amazon.com/s3/buckets/staging-user-media?region=ap-south-1&prefix=$(orgMedia)/&showversions=false"

open-cognito-staging:
	open https://ap-south-1.console.aws.amazon.com/cognito/v2/idp/user-pools/ap-south-1_hWEOvjZUH/users?region=ap-south-1
