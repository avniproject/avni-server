#build stage using gradle
FROM gradle:5.6 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle clean build --no-daemon -x test

#deploy stage to start the java application
FROM openjdk:8 AS deploy
EXPOSE 8021
RUN mkdir /app
COPY --from=build /home/gradle/src/avni-server-api/build/libs/avni-server-0.0.1-SNAPSHOT.jar /app/avni-server-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["java", "-jar","/app/avni-server-0.0.1-SNAPSHOT.jar"]