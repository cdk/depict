FROM maven:3.5-jdk-8-alpine as builder
WORKDIR /usr/src/app
COPY cdkdepict-lib cdkdepict-lib
COPY cdkdepict-webapp cdkdepict-webapp
COPY pom.xml pom.xml
RUN mvn clean package

FROM eclipse-temurin:8u352-b08-jre-alpine as runtime

# we need a font! DejaVu does fine but we only want the Sans-Serif one
RUN apk add --no-cache libgcc && \
    rm /usr/share/fonts/dejavu/DejaVuSerif* \
    /usr/share/fonts/dejavu/DejaVuLGC* \
    /usr/share/fonts/dejavu/DejaVuSansCond* \
    /usr/share/fonts/dejavu/DejaVuSansMono* \
    /usr/share/fonts/dejavu/DejaVuSans-* \
    /usr/share/fonts/dejavu/DejaVuMath*

WORKDIR /usr/src/app
COPY --from=builder /usr/src/app/cdkdepict-webapp/target/cdkdepict-*.war cdkdepict.war
RUN chgrp -R 0 /usr/src/app && chmod -R g=u /usr/src/app
EXPOSE 8080
ENTRYPOINT ["java",  "-Dserver.port=8080", "-jar", "cdkdepict.war"]
