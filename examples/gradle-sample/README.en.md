# Sample C — inventory-service (Gradle)

🇬🇧 English | [🇹🇷 Türkçe](README.md)

Using elk-logger from a Gradle project. **Nothing is downloaded from the internet**: the jar is a file dependency and
only Gradle's core plugins (`java`, `application`) are used.

```bash
gradle --offline run                       # 30 events by default
gradle --offline run --args="0 300"        # until Ctrl+C
gradle --offline installDist               # distribution: build/install/inventory-service/{bin,lib}
build/install/inventory-service/bin/inventory-service 100 50
```

> **No Gradle wrapper (`gradlew`)**: the wrapper downloads Gradle from the internet. Use a Gradle installed on the
> offline machine (copy the distribution zip once, `unzip`, add to `PATH`). Tested with 8.14.3.

> **Gradle version:** Gradle **9.x itself requires JDK 17+**. If your server only has Java 11, use **Gradle 8.x**.
> With JDK 17+ Gradle 9 works too; `options.release = 11` still produces Java 11 bytecode that runs on Java 11.

## Ways to add the dependency

**Groovy DSL (`build.gradle`):**
```groovy
dependencies {
    implementation files('libs/elk-logger-1.0.0.jar')                // 1) from a file (offline, recommended)
    // implementation fileTree(dir: 'libs', include: ['*.jar'])     // 2) every jar under libs/
    // implementation 'io.github.elkboilerplate:elk-logger:1.0.0'   // 3) mavenLocal() or a company repository
}
```

**Kotlin DSL (`build.gradle.kts`):**
```kotlin
dependencies {
    implementation(files("libs/elk-logger-1.0.0.jar"))
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    // implementation("io.github.elkboilerplate:elk-logger:1.0.0")
}
```

Repository for option 3:
```groovy
repositories {
    mavenLocal()                                   // if installed into ~/.m2 with libs/install-to-local-maven-repo.sh
    // maven { url 'http://nexus.local/repository/maven-releases'; allowInsecureProtocol = true }
}
```

Gradle project that uses SLF4J:
```groovy
implementation files('libs/slf4j-api-2.0.13.jar')
runtimeOnly    files('libs/elk-logger-slf4j-1.0.0.jar', 'libs/elk-logger-1.0.0.jar')
```

## Configuration

`src/main/resources/elk-logger.properties` (same as Maven). To use a different file with the `installDist` distribution:
```bash
JAVA_OPTS="-Delk.config.file=/etc/inventory/elk-logger.properties" build/install/inventory-service/bin/inventory-service
```
