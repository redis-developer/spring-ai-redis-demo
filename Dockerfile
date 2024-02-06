FROM eclipse-temurin:21 AS build
WORKDIR /app
COPY . /app
RUN ./mvnw verify

FROM eclipse-temurin:21

EXPOSE 8080

RUN mkdir /app

COPY --from=build /app/target/*.jar /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]