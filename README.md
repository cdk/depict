# CDK Depict

A small web service application for generating chemical structure depictions. 

#### Build

```
$ mvn clean install
```

This generates a web archive (WAR) and a runnable java archive (JAR) in the
target directory. The WAR file can be deployed to an application server (e.g. 
TomCat, Jetty, GlassFish, JBOSS) whilst the JAR launches it's own embedded server.

```
$ target/cdkdepict-0.1.war
$ target/cdkdepict-0.1.jar
```

#### Prepacked release

You can download prebuilt releases from GitHub:
 
 * [`cdkdepict-0.1.war`](https://github.com/cdk/depict/releases/download/0.1/cdkdepict-0.1.war)
 * [`cdkdepict-0.1.jar`](https://github.com/cdk/depict/releases/download/0.1/cdkdepict-0.1.jar)

#### Embedded App

When launching the embedded application the HTTP port is optional (default: 8080). 
Run the following command and access the site 'http://localhost:8081' by web
browser.

```
$ java -jar target/cdkdepict-0.1.jar -httpPort 8081
```

#### Libraries

 * [Spring](http://spring.io/)
 * [Chemistry Development Kit](http://github.com/cdk/cdk)

#### License

LGPL v2.1 or later
