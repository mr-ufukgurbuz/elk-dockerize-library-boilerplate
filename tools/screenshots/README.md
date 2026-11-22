# Dokümantasyon ekran görüntüleri

`docs/images/*.png` dosyaları bu araçlarla, çalışan bir ELK ortamında üretildi.
Sadece dokümanları güncellemek için gereklidir; offline sunucuda kullanılmaz.

Gereksinim: Node.js + Playwright (Chromium), çalışan ELK, örnek uygulamalardan gelen veri.

```bash
cd elk-stack && ./bin/elk.sh up && cd ..
./examples/run-sample.sh order 0 300 &      # sürekli veri
./examples/run-sample.sh payment 0 400 &
elk-stack/bin/elk.sh project create order-service --user order_viewer --password OrderView123
```

Kibana görüntüleri:

```bash
ELASTIC_PASSWORD=$(grep ^ELASTIC_PASSWORD elk-stack/.env | cut -d= -f2) \
PROJECT_USER=order_viewer PROJECT_PASS=OrderView123 \
  node tools/screenshots/take-kibana-screenshots.js
```

Tek bir görüntüyü yenilemek: `ONLY=kibana-spaces.png node tools/screenshots/take-kibana-screenshots.js`

Terminal görüntüleri:

```bash
elk-stack/bin/elk.sh status 2>&1 | python3 tools/screenshots/render-terminal.py "elk status" "$ sudo elk status" > /tmp/t.html
node tools/screenshots/screenshot-html.js /tmp/t.html docs/images/terminal-elk-status.png
```
