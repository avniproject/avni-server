FROM amazoncorretto:21
COPY avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar /opt/openchs/avni-server.jar
CMD java $OPENCHS_SERVER_OPTS $DEBUG_OPTS -jar /opt/openchs/avni-server.jar