# Örnek Proje D — billing-batch (Maven/Gradle YOK, düz Java)

[🇬🇧 English](README.en.md) | 🇹🇷 Türkçe

Build aracı kullanmayan, `javac`/`java` ile derlenip çalıştırılan klasik bir Java 11 projesinde
elk-logger kullanımı. Kütüphane **tek jar**dır ve hiçbir bağımlılığı yoktur; yapmanız gereken tek şey
jar'ı uygulamanın **classpath**'ine eklemek ve `elk-logger.properties` dosyasını bulunabilir bir yere koymaktır.

```
plain-java-sample/
├── src/com/example/billing/BillingBatchApp.java
├── lib/elk-logger-1.0.0.jar          <- kütüphane (build.sh dist/'ten kopyalar; offline pakette hazır)
├── config/elk-logger.properties      <- proje adı, ELK adresi, seviye...
├── application.classpath             <- java @argfile: classpath tek dosyada (Linux)
├── application-windows.classpath     <- aynısı (Windows)
├── build.sh / build.bat              <- javac + çalıştırılabilir jar
├── run.sh  / run.bat                 <- 6 farklı classpath yöntemi
└── .classpath / .project             <- Eclipse projesi
```

## Derle ve çalıştır

```bash
./build.sh                 # Windows: build.bat
./run.sh                   # Windows: run.bat
ELK_HOST=10.0.0.5 ./run.sh cp 100 50
```

## Jar'ı classpath'e eklemenin yolları

Hepsi bu örnekte test edilmiştir (`./run.sh <mod>`). Windows'ta ayraç `:` yerine `;` dır.

| Mod | Komut | Ne zaman |
|---|---|---|
| `cp` | `java -cp "build/classes:lib/*:config" com.example.billing.BillingBatchApp` | En yaygın. `lib/*` dizindeki tüm jar'ları ekler; `config` dizini classpath'te olduğu için `elk-logger.properties` bulunur |
| `argfile` | `java @application.classpath com.example.billing.BillingBatchApp` | Classpath'i uzun komut satırı yerine bir dosyada tutmak (`application.classpath`) — Java 9+ |
| `env` | `export CLASSPATH="build/classes:lib/*:config"; java com.example...` | Başlatma script'i değiştirilemeyen ortamlar |
| `jar` | `java -jar billing-batch.jar` | Tek komutla dağıtım. Jar'ın MANIFEST'inde `Class-Path: lib/elk-logger-1.0.0.jar config/` vardır (yollar jar'ın bulunduğu dizine göredir) |
| `workdir` | `java -cp "build/classes:lib/*" ...` (config classpath'te değil) | Kütüphane çalışma dizininde `elk-logger.properties`, `config/elk-logger.properties`, `conf/elk-logger.properties` dosyalarını kendisi arar |
| `file` | `java -Delk.config.file=/etc/billing/elk-logger.properties -cp ...` | Konfigürasyon uygulamadan ayrı bir yerde (prod) |

> `java -jar` kullanıldığında `-cp` ve `CLASSPATH` **yok sayılır**; jar'ları MANIFEST `Class-Path` ile vermelisiniz.

### Mevcut bir başlatma script'ine eklemek

```bash
# önce
java -cp "app.jar:lib/commons.jar" com.acme.Main
# sonra (elk-logger jar'ı + konfig dizini)
java -cp "app.jar:lib/commons.jar:lib/elk-logger-1.0.0.jar:config" com.acme.Main
```

## IDE

- **Eclipse:** *File → Import → Existing Projects into Workspace* → bu dizin. `.classpath` dosyası jar'ı ve `config/` dizinini ekler.
  Kendi projenizde: sağ tık → *Build Path → Add External Archives…* → `elk-logger-1.0.0.jar`;
  `config` dizini için *Build Path → Configure → Libraries → Add Class Folder*.
- **IntelliJ IDEA:** *File → Project Structure → Modules → Dependencies → + → JARs or Directories* → `lib/elk-logger-1.0.0.jar`
  ve `config` dizini (*Classes* olarak). Run configuration'da çalışma dizini proje kökü olursa `workdir` yöntemi de çalışır.
- **NetBeans:** Proje → *Properties → Libraries → Add JAR/Folder*.

## Uygulama sunucusu / servlet container (WAR)

Tomcat/Jetty'de `WEB-INF/lib/elk-logger-1.0.0.jar`, `WEB-INF/classes/elk-logger.properties`.
Aynı sunucuda birden fazla uygulama varsa her WAR kendi jar'ını ve kendi `elk.project.name`'ini taşımalıdır
(jar'ı paylaşılan `lib/` dizinine koymayın; aksi halde tüm uygulamalar tek konfigürasyonu paylaşır).

## SLF4J kullanan düz Java projesi

`lib/` dizinine ek olarak `slf4j-api-2.0.13.jar` ve `elk-logger-slf4j-1.0.0.jar` koyun; `-cp "...:lib/*:config"` yeterlidir.
Classpath'te başka bir SLF4J provider (logback vb.) olmamalıdır.
