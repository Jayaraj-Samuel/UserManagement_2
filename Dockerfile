# Multi-stage Docker build for Java 17 + Tomcat 9 Web Application
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM tomcat:9.0-jdk17

# Clear default webapps and deploy usermanagement.war as root application
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=builder /app/target/usermanagement.war /usr/local/tomcat/webapps/ROOT.war

# Enable IPv6 preference for Railway private networking (railway.internal)
ENV JAVA_OPTS="-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Stack=false"

EXPOSE 8080

CMD ["catalina.sh", "run"]
