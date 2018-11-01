#!/bin/bash

cd ../ && \
    mvn install -DskipTests && \
    cd docker && \
    cp ../cdkdepict-webapp/target/cdkdepict*.jar cdkdepict.jar && \
    docker build -t cdkdepict -f Dockerfile .
