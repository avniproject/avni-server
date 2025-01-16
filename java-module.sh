rm -rf avni-server-data/src/main/java/org/avni
mkdir -p avni-server-data/src/main/java/org/avni/server
git mv avni-server-api/src/main/java/org/avni/server/domain avni-server-data/src/main/java/org/avni/server/

mkdir -p avni-server-data/src/main/java/org/avni/server/framework/security
git mv avni-server-api/src/main/java/org/avni/server/framework/security/UserContextHolder.java avni-server-data/src/main/java/org/avni/server/framework/security/UserContextHolder.java

mkdir -p avni-server-data/src/main/java/org/avni/server/util/
git mv avni-server-api/src/main/java/org/avni/server/util/DateTimeUtil.java avni-server-data/src/main/java/org/avni/server/util/DateTimeUtil.java
git mv avni-server-api/src/main/java/org/avni/server/util/ObjectMapperSingleton.java avni-server-data/src/main/java/org/avni/server/util/ObjectMapperSingleton.java
git mv avni-server-api/src/main/java/org/avni/server/util/ValidationUtil.java avni-server-data/src/main/java/org/avni/server/util/ValidationUtil.java
git mv avni-server-api/src/main/java/org/avni/server/util/S.java avni-server-data/src/main/java/org/avni/server/util/S.java
git mv avni-server-api/src/main/java/org/avni/server/util/CollectionUtil.java avni-server-data/src/main/java/org/avni/server/util/CollectionUtil.java
git mv avni-server-api/src/main/java/org/avni/server/util/S3File.java avni-server-data/src/main/java/org/avni/server/util/S3File.java
git mv avni-server-api/src/main/java/org/avni/server/util/S3FileType.java avni-server-data/src/main/java/org/avni/server/util/S3FileType.java

mkdir -p avni-server-data/src/main/java/org/avni/server/framework
git mv avni-server-api/src/main/java/org/avni/server/framework/hibernate avni-server-data/src/main/java/org/avni/server/framework

git mv avni-server-api/src/main/java/org/avni/server/application avni-server-data/src/main/java/org/avni/server/

mkdir -p avni-server-data/src/main/java/org/avni/server/common
git mv avni-server-api/src/main/java/org/avni/server/common/ValidationResult.java avni-server-data/src/main/java/org/avni/server/common/ValidationResult.java

git mv avni-server-api/src/main/java/org/avni/server/geo avni-server-data/src/main/java/org/avni/server/

git mv avni-server-api/src/main/java/org/avni/server/ltree avni-server-data/src/main/java/org/avni/server/

mkdir -p avni-server-api/src/main/java/org/avni/server/framework/hibernate
git mv avni-server-data/src/main/java/org/avni/server/framework/hibernate/DummyInterceptor.java avni-server-api/src/main/java/org/avni/server/framework/hibernate/DummyInterceptor.java
git mv avni-server-data/src/main/java/org/avni/server/framework/hibernate/CacheEventLogger.java avni-server-api/src/main/java/org/avni/server/framework/hibernate/CacheEventLogger.java

mkdir -p avni-server-data/src/main/java/org/avni/messaging
git mv avni-server-api/src/main/java/org/avni/messaging/domain avni-server-data/src/main/java/org/avni/messaging/

git mv avni-server-api/src/main/java/org/avni/server/common/dbSchema avni-server-data/src/main/java/org/avni/server/common/

git mv avni-server-api/src/main/java/org/avni/server/projection avni-server-data/src/main/java/org/avni/server/



mkdir -p avni-server-api/src/main/java/org/avni/server/domain
git mv avni-server-data/src/main/java/org/avni/server/domain/RuleExecutionException.java avni-server-api/src/main/java/org/avni/server/domain/RuleExecutionException.java
mkdir -p avni-server-api/src/main/java/org/avni/server/domain/metabase
git mv avni-server-data/src/main/java/org/avni/server/domain/metabase/CollectionPermissionsService.java avni-server-api/src/main/java/org/avni/server/domain/metabase/CollectionPermissionsService.java
