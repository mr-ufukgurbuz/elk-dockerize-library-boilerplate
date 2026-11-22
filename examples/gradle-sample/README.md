# Örnek Proje C — inventory-service (Gradle)

[🇬🇧 English](README.en.md) | 🇹🇷 Türkçe

Gradle ile derlenen bir projede elk-logger kullanımı. **İnternetten hiçbir şey indirmez**: jar dosyadan eklenir,
yalnızca Gradle'ın çekirdek eklentileri (`java`, `application`) kullanılır.

```bash
gradle --offline run                       # varsayılan 30 olay
gradle --offline run --args="0 300"        # Ctrl+C'ye kadar
gradle --offline installDist               # dağıtım: build/install/inventory-service/{bin,lib}
build/install/inventory-service/bin/inventory-service 100 50
```

> **Gradle wrapper (`gradlew`) yok**: wrapper Gradle'ı internetten indirir. Offline makinede kurulu bir Gradle 8.x
> kullanın (Gradle dağıtım zip'ini bir kez taşıyıp `unzip` + `PATH` yeterli). Test edilen sürüm: 8.14.3.

> **Gradle sürümü:** Gradle **9.x kendisi JDK 17+ ile çalışır**. Sunucunuzda sadece Java 11 varsa **Gradle 8.x**
> (test edilen: 8.14.3) kullanın. JDK 17+ varsa Gradle 9 de olur; `options.release = 11` sayesinde uygulama yine
> Java 11 bytecode'u üretir ve Java 11'de çalışır.

## Bağımlılığı ekleme seçenekleri (`build.gradle`)

**Groovy DSL (`build.gradle`):**
```groovy
dependencies {
    implementation files('libs/elk-logger-1.0.0.jar')                // 1) dosyadan (offline, önerilen)
    // implementation fileTree(dir: 'libs', include: ['*.jar'])     // 2) libs/ altındaki tüm jar'lar
    // implementation 'io.github.elkboilerplate:elk-logger:1.0.0'   // 3) mavenLocal() veya kurumsal depo
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

Seçenek 3 için depo tanımı:
```groovy
repositories {
    mavenLocal()                                   // libs/install-to-local-maven-repo.sh ile ~/.m2'ye kurulduysa
    // maven { url 'http://nexus.local/repository/maven-releases'; allowInsecureProtocol = true }
}
```

SLF4J kullanan Gradle projesi:
```groovy
implementation files('libs/slf4j-api-2.0.13.jar')
runtimeOnly    files('libs/elk-logger-slf4j-1.0.0.jar', 'libs/elk-logger-1.0.0.jar')
```

## Konfigürasyon

`src/main/resources/elk-logger.properties` (Maven ile aynı). `installDist` dağıtımında farklı bir dosya vermek için:
```bash
JAVA_OPTS="-Delk.config.file=/etc/inventory/elk-logger.properties" build/install/inventory-service/bin/inventory-service
```
