#!/bin/bash

cd ../ && \
    mvn install -DskipTests && \
    cd docker && \
    cp ../cdkdepict-webapp/target/cdkdepict*.war cdkdepict.war && \
    docker build -t cdkdepict -f Dockerfile .
