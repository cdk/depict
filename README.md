# CDK Depict

A web application for generating chemical structure depictions from SMILES.

## [https://www.simolecule.com/cdkdepict](http://www.simolecule.com/cdkdepict)

## Docker

An image is available on DockerHub, [https://hub.docker.com/r/simolecule/cdkdepict/](https://hub.docker.com/r/simolecule/cdkdepict/). To launch a CDK Depict web serivce running on 8081:

```
$ docker run -p 8081:8080 simolecule/cdkdepict:1.6
```

#### Prepacked release

You can download prebuilt releases from GitHub:
 
 * [`cdkdepict-1.7.war`](https://github.com/cdk/depict/releases/download/1.7/cdkdepict-1.7.war)
 * [`cdkdepict-1.7.jar`](https://github.com/cdk/depict/releases/download/1.7/cdkdepict-1.7.jar)

#### Build

```
$ mvn clean install
```

This generates a web archive (WAR) and a runnable java archive (JAR) in the
target directory. The WAR file can be deployed to an application server (e.g. 
TomCat, Jetty, GlassFish, JBOSS) whilst the JAR launches it's own embedded server.

```
$ target/cdkdepict-1.7.war
$ target/cdkdepict-1.7.jar
```

#### Embedded App

When launching the embedded application the HTTP port is optional (default: 8080). 
Run the following command and access the site 'http://localhost:8081' by web
browser.

```
$ java -jar target/cdkdepict-1.7.jar -httpPort 8081
```

#### Caching

When running your own CDK Depict it's useful to enable caching of the generated
content. To enable caching you need to add the following setting to either the
WebApp or TomCat's global ``context.xml``:

```
<Valve className="org.apache.catalina.authenticator.BasicAuthenticator"
  disableProxyCaching="false" />
```

### Docker container

A docker container (using alpine linux) can be built and run as follows:

```
$ cd docker && ./build.sh
$ docker run -p 8180:8080 cdkdepict
```

#### Libraries

 * [Spring](http://spring.io/)
 * [Chemistry Development Kit](http://github.com/cdk/cdk)
 * [Centres](http://github.com/simolecule/cdkdepict)

#### License

LGPL v2.1 or later

