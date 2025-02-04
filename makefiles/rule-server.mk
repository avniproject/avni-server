start-rule-server: build-server
	./gradlew :avni-rule-server:run

build-js-rule-server:
	#cd avni-rule-server/src/main/js && npm install
	./gradlew :avni-rule-server:copyJsFiles

deps:
	cd avni-rule-server/src/main/js && npm install --legacy-peer-deps

build-rule-invoker:
	cd avni-rule-server/src/main/js && npm run build
