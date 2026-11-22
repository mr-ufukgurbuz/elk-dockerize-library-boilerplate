# Hazır derlenmiş jar'lar

| Dosya | Açıklama |
|---|---|
| `elk-logger-1.0.0.jar` | Ana kütüphane. **Tek başına yeterlidir**, hiçbir bağımlılığı yoktur (Java 11+). |
| `elk-logger-1.0.0-sources.jar` | Kaynak kodu (IDE'de gezinmek için). |
| `elk-logger-slf4j-1.0.0.jar` | Opsiyonel SLF4J 2.x provider. `elk-logger` + `slf4j-api` 2.0.x ile birlikte kullanılır. |

Yeniden üretmek: `mvn -B install` (veya Maven yoksa `./libs/build-without-maven.sh`).
Projeye ekleme yöntemleri: [docs/06-JAVA-KUTUPHANE.md](../docs/06-JAVA-KUTUPHANE.md).
