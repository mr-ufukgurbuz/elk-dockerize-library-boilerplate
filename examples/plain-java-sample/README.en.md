# Sample D — billing-batch (no Maven/Gradle, plain Java)

🇬🇧 English | [🇹🇷 Türkçe](README.md)

Using elk-logger in a classic Java 11 project that is compiled and run with plain `javac`/`java`.
The library is a **single jar** with no dependencies; all you need is to put the jar on the application's **classpath**
and place `elk-logger.properties` somewhere it can be found.

```
plain-java-sample/
├── src/com/example/billing/BillingBatchApp.java
├── lib/elk-logger-1.0.0.jar          <- the library (build.sh copies it from dist/; ready in the offline bundle)
├── config/elk-logger.properties      <- project name, ELK address, levels...
├── application.classpath             <- java @argfile: the classpath in one file (Linux)
├── application-windows.classpath     <- same for Windows
├── build.sh / build.bat              <- javac + executable jar
├── run.sh  / run.bat                 <- 6 different classpath approaches
└── .classpath / .project             <- Eclipse project
```

## Build and run

```bash
./build.sh                 # Windows: build.bat
./run.sh                   # Windows: run.bat
ELK_HOST=10.0.0.5 ./run.sh cp 100 50
```

## Ways to put the jar on the classpath

All of them are tested in this sample (`./run.sh <mode>`). On Windows the separator is `;` instead of `:`.

| Mode | Command | When |
|---|---|---|
| `cp` | `java -cp "build/classes:lib/*:config" com.example.billing.BillingBatchApp` | Most common. `lib/*` adds every jar in the directory; `config` is on the classpath so `elk-logger.properties` is found |
| `argfile` | `java @application.classpath com.example.billing.BillingBatchApp` | Keep the classpath in a file (`application.classpath`) instead of a long command line — Java 9+ |
| `env` | `export CLASSPATH="build/classes:lib/*:config"; java com.example...` | Environments where the start script cannot be changed |
| `jar` | `java -jar billing-batch.jar` | Single-command distribution. The jar's MANIFEST has `Class-Path: lib/elk-logger-1.0.0.jar config/` (paths relative to the jar's directory) |
| `workdir` | `java -cp "build/classes:lib/*" ...` (config not on the classpath) | The library itself looks for `elk-logger.properties`, `config/elk-logger.properties`, `conf/elk-logger.properties` in the working directory |
| `file` | `java -Delk.config.file=/etc/billing/elk-logger.properties -cp ...` | Configuration kept outside the application (production) |

> With `java -jar`, `-cp` and `CLASSPATH` are **ignored**; jars must be listed in the MANIFEST `Class-Path`.

### Adding it to an existing start script

```bash
# before
java -cp "app.jar:lib/commons.jar" com.acme.Main
# after (elk-logger jar + config directory)
java -cp "app.jar:lib/commons.jar:lib/elk-logger-1.0.0.jar:config" com.acme.Main
```

## IDE

- **Eclipse:** *File → Import → Existing Projects into Workspace* → this directory. The `.classpath` file adds the jar and the `config/` directory.
  In your own project: right click → *Build Path → Add External Archives…* → `elk-logger-1.0.0.jar`;
  for the `config` directory use *Build Path → Configure → Libraries → Add Class Folder*.
- **IntelliJ IDEA:** *File → Project Structure → Modules → Dependencies → + → JARs or Directories* → `lib/elk-logger-1.0.0.jar`
  and the `config` directory (as *Classes*). If the run configuration's working directory is the project root, the `workdir` approach also works.
- **NetBeans:** Project → *Properties → Libraries → Add JAR/Folder*.

## Application server / servlet container (WAR)

Tomcat/Jetty: `WEB-INF/lib/elk-logger-1.0.0.jar`, `WEB-INF/classes/elk-logger.properties`.
If several applications run on the same server, each WAR must carry its own jar and its own `elk.project.name`
(do not put the jar into the shared `lib/` directory, otherwise all applications share one configuration).

## Plain Java project that uses SLF4J

Also put `slf4j-api-2.0.13.jar` and `elk-logger-slf4j-1.0.0.jar` into `lib/`; `-cp "...:lib/*:config"` is enough.
There must be no other SLF4J provider (logback etc.) on the classpath.
