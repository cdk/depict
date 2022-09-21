# Build

To build the 'cdk/depict' image directly from source (including the MAVEN build)

```
docker/$ docker build -t cdkdepict -f Dockerfile ..
```

or from the project main directory:

```
$ docker build -t cdkdepict -f docker/Dockerfile .
```


# Run

The web service is exposed through port 8080. To expose locally on port 8180 we
run the following command.

	docker run -it -p 8180:8080 cdkdepict

Note that when using docker-machine (e.g. VirtualBox) you will need to forward
the host/guest port there as well.
