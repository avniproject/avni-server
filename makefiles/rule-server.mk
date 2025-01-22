start-rule-server: build-server
	./gradlew :avni-rule-server:run

build-js-rule-server:
	cd avni-rule-server/src/main/js && npm install
