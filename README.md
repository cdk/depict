# CDK Depict

A web application for generating chemical structure depictions from SMILES.

## Public Instance: [http://www.simolecule.com/cdkdepict](http://www.simolecule.com/cdkdepict)

## Docker

To launch a CDK Depict web serivce running on 8081:

```
$ docker run -p 8081:8080 simolecule/cdkdepict
```

#### Prepacked release

You can download prebuilt releases from GitHub:
 
 * [`cdkdepict-0.3.war`](https://github.com/cdk/depict/releases/download/0.3/cdkdepict-0.3.war)
 * [`cdkdepict-0.3.jar`](https://github.com/cdk/depict/releases/download/0.3/cdkdepict-0.3.jar)

#### Build

```
$ mvn clean install
```

This generates a web archive (WAR) and a runnable java archive (JAR) in the
target directory. The WAR file can be deployed to an application server (e.g. 
TomCat, Jetty, GlassFish, JBOSS) whilst the JAR launches it's own embedded server.

```
$ target/cdkdepict-0.3.war
$ target/cdkdepict-0.3.jar
```

#### Embedded App

When launching the embedded application the HTTP port is optional (default: 8080). 
Run the following command and access the site 'http://localhost:8081' by web
browser.

```
$ java -jar target/cdkdepict-0.3.jar -httpPort 8081
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
