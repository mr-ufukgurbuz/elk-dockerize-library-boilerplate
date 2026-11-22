# 10 — Güvenlik

## 1. Varsayılan güvenlik modeli

| Katman | Durum |
|---|---|
| Elasticsearch kimlik doğrulama | **Açık.** Tüm istekler kullanıcı/şifre ister. |
| Şifreler | `install.sh`/`elk init-secrets` her kurulumda **rastgele** üretir; `/opt/elk-stack/.env` (chmod 600). |
| Uygulama → Logstash | Kimlik doğrulama yok (TCP), HTTP'de opsiyonel basic auth. Ağ seviyesinde sınırlandırın. |
| TLS | Kapalı (kapalı/izole ağ varsayımı). Aşağıda açma adımları. |
| Uygulamaların ES şifresi | Uygulamalar ES'ye hiç bağlanmaz; ES şifresini bilmez. |
| Proje izolasyonu | `project_<p>_reader` rolü + Kibana Space (bkz. [05 §3](05-KIBANA.md#3-proje-bazlı-alanlar-spaces)). |

## 2. Önerilen sıkılaştırma (kolaydan zora)

1. **Portları daraltın:** 9200 ve 9600'ü dışarı açmayın (compose'dan `ports` satırını silin veya
   `127.0.0.1:` ile bağlayın). Kibana ve Logstash portları yeterli.
2. **Firewall ile kaynak IP kısıtlayın:**
   ```bash
   sudo firewall-cmd --permanent --add-rich-rule='rule family=ipv4 source address=10.10.0.0/16 port port=5000 protocol=tcp accept'
   ```
3. **`elastic` kullanıcısını günlük kullanmayın:** Kibana'da kişisel kullanıcılar açın
   (Stack Management → Users), `elk project create ... --user` ile proje kullanıcıları.
4. **HTTP input'a basic auth:** [04 §5](04-LOGSTASH.md#5-değişiklik-örnekleri).
5. **Hassas veri:** Kütüphaneye kişisel veri/şifre loglamayın; gerekirse Logstash'te maskeleyin ([04 §5](04-LOGSTASH.md#5-değişiklik-örnekleri)).
6. **TLS** (aşağıda).

## 3. TLS açma

Offline ortamda sertifikalar Elasticsearch imajındaki `elasticsearch-certutil` ile üretilebilir:

```bash
cd /opt/elk-stack && mkdir -p certs
docker run --rm -v $PWD/certs:/certs elasticsearch:8.19.25 bash -c '
  bin/elasticsearch-certutil ca --silent --pem --out /certs/ca.zip && cd /certs && unzip -o ca.zip &&
  printf "instances:\n  - name: elk\n    dns: [elk.local, localhost]\n    ip: [10.0.0.5, 127.0.0.1]\n" > /tmp/i.yml &&
  /usr/share/elasticsearch/bin/elasticsearch-certutil cert --silent --pem --in /tmp/i.yml --ca-cert ca/ca.crt --ca-key ca/ca.key --out /certs/elk.zip &&
  unzip -o elk.zip && chmod -R a+r /certs'
```
(`dns`/`ip` alanlarını sunucunuza göre düzenleyin.)

### 3.1 Uygulama → Logstash TLS
`docker-compose.yml` → logstash `volumes`: `- ./certs:/usr/share/logstash/certs:ro,z`

`applogs.conf`:
```
tcp  { port => 5000 codec => json_lines ssl_enabled => true
       ssl_certificate => "/usr/share/logstash/certs/elk/elk.crt"
       ssl_key => "/usr/share/logstash/certs/elk/elk.key" ... }
http { port => 8080 ... ssl_enabled => true
       ssl_certificate => "/usr/share/logstash/certs/elk/elk.crt"
       ssl_key => "/usr/share/logstash/certs/elk/elk.key" }
```
Java tarafı: CA'yı truststore'a koyun ve kütüphaneyi yapılandırın:
```bash
keytool -importcert -noprompt -alias elk-ca -file certs/ca/ca.crt -keystore elk-truststore.p12 -storetype PKCS12 -storepass changeit
```
```properties
elk.tls.enabled=true
elk.tls.truststore.path=/etc/app/elk-truststore.p12
elk.tls.truststore.password=changeit
elk.logstash.host=elk.local        # sertifikadaki dns/ip ile eşleşmeli
```

### 3.2 Kibana HTTPS (tarayıcı)
`kibana.yml`:
```yaml
server.ssl.enabled: true
server.ssl.certificate: /usr/share/kibana/certs/elk/elk.crt
server.ssl.key: /usr/share/kibana/certs/elk/elk.key
```
+ compose'da certs volume, `.env` → `KIBANA_PUBLIC_URL=https://...`, healthcheck URL'lerini `https` + `-k` yapın.

### 3.3 Elasticsearch HTTP TLS
`elasticsearch.yml`'de `xpack.security.http.ssl.enabled: true` ve sertifika yolları; ardından **tüm**
`http://elasticsearch:9200` adreslerini (logstash.yml, applogs.conf, kibana.yml, setup scriptleri, healthcheck)
`https://` yapıp CA'yı ilgili yerlere tanıtın (`ssl_certificate_authorities`, `elasticsearch.ssl.certificateAuthorities`,
`curl --cacert`). Bu değişiklik geniş kapsamlıdır; önce test ortamında deneyin.

## 4. Şifre değiştirme

```bash
# elastic
docker exec -it elk-elasticsearch bin/elasticsearch-reset-password -u elastic -i
# .env'deki ELASTIC_PASSWORD'u güncelleyin, sonra:
sudo elk up
```
Diğer dahili kullanıcılar (`kibana_system`, `logstash_system`, `logstash_writer`): `.env`'de değeri değiştirip
`sudo elk up` — setup servisi yeni şifreyi ES'ye uygular, servisler yeni şifreyle yeniden oluşturulur.

## 5. Denetim

Kim ne aradı (audit log) Basic lisansta yoktur. Uygulama tarafında kritik işlemler için
`Marker`/`tag("AUDIT")` ile ayrı etiketlenmiş loglar yazıp `tags : AUDIT` ile izleyebilirsiniz.
