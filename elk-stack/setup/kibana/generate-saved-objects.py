#!/usr/bin/env python3
"""
applogs-saved-objects.ndjson dosyasini uretir (SADECE GELISTIRME ZAMANI; offline sunucuda gerekmez).

  python3 generate-saved-objects.py > applogs-saved-objects.ndjson

Kurallar (bin/elk.sh 'project create' bu kurallara guvenir):
  * Tum nesne id'leri 'applogs-all' ile baslar  -> proje icin 'applogs-<proje>' ile degistirilir
  * Data view title'i tam olarak 'applogs-*'    -> proje icin 'applogs-<proje>-*'
  * Basliklarda '(Tum Projeler)' ifadesi         -> proje icin '(<proje>)'
"""
import json

DV = "applogs-all"
REF = "kibanaSavedObjectMeta.searchSourceJSON.index"


def search_source(query="", with_index=True):
    s = {"query": {"query": query, "language": "kuery"}, "filter": []}
    if with_index:
        s["indexRefName"] = REF
    return {"searchSourceJSON": json.dumps(s)}


def vis(oid, title, vis_state, query=""):
    vis_state = dict(vis_state, title=title)
    return {
        "type": "visualization", "id": oid,
        "attributes": {
            "title": title, "description": "", "version": 1, "uiStateJSON": "{}",
            "visState": json.dumps(vis_state),
            "kibanaSavedObjectMeta": search_source(query),
        },
        "references": [{"name": REF, "type": "index-pattern", "id": DV}],
    }


def count_metric():
    return {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}}


def metric_vis(label_color="None"):
    return {
        "type": "metric",
        "aggs": [count_metric()],
        "params": {
            "addTooltip": True, "addLegend": False, "type": "metric",
            "metric": {
                "percentageMode": False, "useRanges": False, "colorSchema": "Green to Red",
                "metricColorMode": label_color, "colorsRange": [{"from": 0, "to": 10000}],
                "labels": {"show": True}, "invertColors": False,
                "style": {"bgFill": "#000", "bgColor": False, "labelColor": False, "subText": "", "fontSize": 48},
            },
        },
    }


def histogram_by(field, size=6, interval="auto"):
    return {
        "type": "histogram",
        "aggs": [
            count_metric(),
            {"id": "2", "enabled": True, "type": "date_histogram", "schema": "segment",
             "params": {"field": "@timestamp", "useNormalizedEsInterval": True, "interval": interval,
                        "drop_partials": False, "min_doc_count": 1, "extended_bounds": {}}},
            {"id": "3", "enabled": True, "type": "terms", "schema": "group",
             "params": {"field": field, "orderBy": "1", "order": "desc", "size": size,
                        "otherBucket": False, "missingBucket": False}},
        ],
        "params": {
            "type": "histogram", "grid": {"categoryLines": False}, "addTooltip": True, "addLegend": True,
            "legendPosition": "right", "times": [], "addTimeMarker": False,
            "categoryAxes": [{"id": "CategoryAxis-1", "type": "category", "position": "bottom", "show": True,
                              "style": {}, "scale": {"type": "linear"}, "labels": {"show": True, "filter": True, "truncate": 100},
                              "title": {}}],
            "valueAxes": [{"id": "ValueAxis-1", "name": "LeftAxis-1", "type": "value", "position": "left", "show": True,
                           "style": {}, "scale": {"type": "linear", "mode": "normal"},
                           "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
                           "title": {"text": "Log sayisi"}}],
            "seriesParams": [{"show": True, "type": "histogram", "mode": "stacked",
                              "data": {"label": "Log sayisi", "id": "1"}, "valueAxis": "ValueAxis-1",
                              "drawLinesBetweenPoints": True, "lineWidth": 2, "showCircles": True}],
            "thresholdLine": {"show": False, "value": 10, "width": 1, "style": "full", "color": "#E7664C"},
            "labels": {"show": False},
        },
    }


def pie_by(field, size=10):
    return {
        "type": "pie",
        "aggs": [count_metric(),
                 {"id": "2", "enabled": True, "type": "terms", "schema": "segment",
                  "params": {"field": field, "orderBy": "1", "order": "desc", "size": size,
                             "otherBucket": True, "otherBucketLabel": "Diger", "missingBucket": False}}],
        "params": {"type": "pie", "addTooltip": True, "legendDisplay": "show", "legendPosition": "right",
                   "isDonut": True, "nestedLegend": False, "distinctColors": True, "truncateLegend": True,
                   "maxLegendLines": 1, "labels": {"show": True, "values": True, "last_level": True,
                                                   "truncate": 100, "position": "default", "valuesFormat": "percent"}},
    }


def table_by(fields, size=10):
    aggs = [count_metric()]
    for i, (field, label) in enumerate(fields, start=2):
        aggs.append({"id": str(i), "enabled": True, "type": "terms", "schema": "bucket",
                     "params": {"field": field, "orderBy": "1", "order": "desc", "size": size,
                                "otherBucket": False, "missingBucket": False, "customLabel": label}})
    return {"type": "table", "aggs": aggs,
            "params": {"perPage": 10, "showPartialRows": False, "showMetricsAtAllLevels": False,
                       "showTotal": False, "totalFunc": "sum", "percentageCol": ""}}


def saved_search(oid, title, query, columns):
    return {
        "type": "search", "id": oid,
        "attributes": {"title": title, "description": "", "columns": columns,
                       "sort": [["@timestamp", "desc"]], "kibanaSavedObjectMeta": search_source(query)},
        "references": [{"name": REF, "type": "index-pattern", "id": DV}],
    }


objects = [
    {
        "type": "index-pattern", "id": DV,
        "attributes": {"title": "applogs-*", "name": "Uygulama Loglari (Tum Projeler)", "timeFieldName": "@timestamp",
                       "fieldFormatMap": "{}"},
        "references": [],
    },
    saved_search(DV + "-search-all", "Tum Loglar (Tum Projeler)", "",
                 ["project", "level", "logger", "message"]),
    saved_search(DV + "-search-errors", "Hata ve Uyarilar (Tum Projeler)", "level:(ERROR or WARN)",
                 ["project", "level", "logger", "message", "exception.class"]),
    vis(DV + "-vis-total", "Toplam Log (Tum Projeler)", metric_vis()),
    vis(DV + "-vis-errors", "ERROR Sayisi (Tum Projeler)", metric_vis(), "level:ERROR"),
    vis(DV + "-vis-warns", "WARN Sayisi (Tum Projeler)", metric_vis(), "level:WARN"),
    vis(DV + "-vis-over-time", "Zamana Gore Log Seviyeleri (Tum Projeler)", histogram_by("level")),
    vis(DV + "-vis-by-project", "Projelere Gore Dagilim (Tum Projeler)", pie_by("project")),
    vis(DV + "-vis-by-host", "Sunuculara Gore Dagilim (Tum Projeler)", pie_by("hostname")),
    vis(DV + "-vis-top-error-loggers", "En Cok Hata Ureten Siniflar (Tum Projeler)",
        table_by([("logger", "Logger"), ("exception.class", "Exception")]), "level:ERROR"),
    vis(DV + "-vis-errors-over-time", "Zamana Gore Hatalar - Projeye Gore (Tum Projeler)",
        histogram_by("project", size=10), "level:ERROR"),
]

# Dashboard yerlesimi: (nesne id, x, y, w, h)
layout = [
    (DV + "-vis-total", 0, 0, 16, 8),
    (DV + "-vis-errors", 16, 0, 16, 8),
    (DV + "-vis-warns", 32, 0, 16, 8),
    (DV + "-vis-over-time", 0, 8, 32, 14),
    (DV + "-vis-by-project", 32, 8, 16, 14),
    (DV + "-vis-errors-over-time", 0, 22, 32, 14),
    (DV + "-vis-by-host", 32, 22, 16, 14),
    (DV + "-vis-top-error-loggers", 0, 36, 48, 14),
    (DV + "-search-errors", 0, 50, 48, 20),
]
by_id = {o["id"]: o for o in objects}
panels, refs = [], []
for i, (oid, x, y, w, h) in enumerate(layout, start=1):
    panels.append({"version": "8.19.0", "type": by_id[oid]["type"], "panelIndex": str(i),
                   "gridData": {"x": x, "y": y, "w": w, "h": h, "i": str(i)},
                   "embeddableConfig": {"enhancements": {}}, "panelRefName": "panel_%d" % i})
    refs.append({"name": "panel_%d" % i, "type": by_id[oid]["type"], "id": oid})

objects.append({
    "type": "dashboard", "id": DV + "-dashboard",
    "attributes": {
        "title": "Uygulama Loglari (Tum Projeler)",
        "description": "elk-logger kutuphanesi ile gonderilen uygulama loglari: seviye dagilimi, hatalar, projeler.",
        "panelsJSON": json.dumps(panels),
        "optionsJSON": json.dumps({"useMargins": True, "syncColors": False, "syncCursor": True,
                                   "syncTooltips": False, "hidePanelTitles": False}),
        "timeRestore": True, "timeFrom": "now-24h", "timeTo": "now",
        "refreshInterval": {"pause": False, "value": 30000},
        "version": 1,
        "kibanaSavedObjectMeta": search_source("", with_index=False),
    },
    "references": refs,
})

for o in objects:
    print(json.dumps(o, ensure_ascii=False))
