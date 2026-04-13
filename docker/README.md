# Build

To build the 'cdk/depict' image directly from source (including the MAVEN build). 

There are two different versions, the newer "jakarta" version uses Spring 6 (default) and requires Java 17/TomCat 10 to deploy. The "javaee" uses the older
APIs and will run on Java 8/TomCat 9 but uses libraries which are no longer
updated.

```
docker/$ docker build -t cdkdepict -f Dockerfile ..
docker/$ docker build -t cdkdepict-javaee -f Dockerfile.javaee ..
```

or from the project main directory:

```
$ docker build -t cdkdepict -f docker/Dockerfile .
$ docker build -t cdkdepict-javaee -f docker/Dockerfile.javaee .
```

# Run

The web service is exposed through port 8080. To expose locally on port 8180 we
run the following command.

	docker run -it -p 8180:8080 cdkdepict

Note that when using docker-machine (e.g. VirtualBox) you will need to forward
the host/guest port there as well.
