FROM openjdk:8u151-jre-alpine

# we need a font! DejaVu does fine but we only want the Sans-Serif one
RUN apk add --no-cache ttf-dejavu && \
    rm /usr/share/fonts/ttf-dejavu/DejaVuSerif* \
    /usr/share/fonts/ttf-dejavu/DejaVuLGC* \
    /usr/share/fonts/ttf-dejavu/DejaVuSansCond* \
    /usr/share/fonts/ttf-dejavu/DejaVuSansMono* \
    /usr/share/fonts/ttf-dejavu/DejaVuSans-* \
    /usr/share/fonts/ttf-dejavu/DejaVuMath*
COPY cdkdepict.jar .
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "cdkdepict.jar", "--httpPort", "8080"]