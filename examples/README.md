# Örnek Projeler

| Proje | API | Transport | Loglama alanı |
|---|---|---|---|
| [`order-service-sample`](order-service-sample) (Proje A) | elk-logger native | TCP 5000 | `applogs-order-service-*` |
| [`payment-service-slf4j-sample`](payment-service-slf4j-sample) (Proje B) | SLF4J + elk-logger-slf4j | HTTP 8080 | `applogs-payment-service-*` |

## Çalıştır

```bash
./examples/run-sample.sh order 200 50       # 200 sipariş, 50 ms arayla
./examples/run-sample.sh payment 100 50
./examples/run-sample.sh order 0 300        # Ctrl+C'ye kadar sürekli
ELK_HOST=10.0.0.5 ./examples/run-sample.sh order   # ELK başka sunucudaysa
```

![örnek uygulama çıktısı](../docs/images/terminal-sample-app.png)

## Sonuç

İki proje ayrı index'lere yazar:

![Index Management](../docs/images/kibana-index-management.png)

`elk project create order-service --user order_viewer ...` ile açılan kullanıcı sadece kendi projesini görür:

![Proje space dashboard](../docs/images/kibana-dashboard-project-space.png)

Detay: [docs/08-ORNEK-PROJELER.md](../docs/08-ORNEK-PROJELER.md).
