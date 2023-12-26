FROM amazoncorretto:17-alpine3.17

RUN mkdir /app
COPY ../build/libs/consul.jar /app/app.jar

WORKDIR /app

ENTRYPOINT ["java","-jar","app.jar"]
#ENTRYPOINT ["tail","-f","/dev/null"]