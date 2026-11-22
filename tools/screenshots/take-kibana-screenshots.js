// Kibana ekran goruntulerini docs/images altina uretir (SADECE dokumantasyon icin, gelistirme makinesinde).
// Gereksinim: calisan ELK (elk-stack/bin/elk.sh up), Node + Playwright (chromium).
//   ELASTIC_PASSWORD=... node tools/screenshots/take-kibana-screenshots.js [kibana_url]
// Proje kullanicisi goruntusu icin: PROJECT_USER=order_viewer PROJECT_PASS=OrderView123
const { chromium } = require('playwright');
const path = require('path');

const KB = process.argv[2] || 'http://localhost:5601';
const OUT = path.resolve(__dirname, '../../docs/images');
const PASS = process.env.ELASTIC_PASSWORD;
const T = 'time:(from:now-15m,to:now)';
const ONLY = process.env.ONLY ? process.env.ONLY.split(',') : null;

async function login(browser, user, pass) {
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  await page.goto(`${KB}/login`);
  await page.fill('input[name=username]', user);
  await page.fill('input[name=password]', pass);
  await page.click('button[type=submit]');
  await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 60000 });
  await page.waitForTimeout(3000);
  return page;
}

async function closePopups(page) {
  for (const sel of ['[data-test-subj="toastCloseButton"]', 'button:has-text("Dismiss")',
                     'button:has-text("Close tour")', 'button:has-text("Got it")',
                     'button:has-text("Remind me later")']) {
    for (const el of await page.$$(sel)) {
      try { await el.click({ timeout: 500 }); } catch (e) { /* yoksay */ }
    }
  }
}

async function shot(page, url, file, { wait = 12000, full = false, before } = {}) {
  if (ONLY && !ONLY.includes(file)) return;
  await page.goto(`${KB}${url}`);
  await page.waitForTimeout(wait);
  await closePopups(page);
  if (before) await before(page);
  await closePopups(page);
  await page.screenshot({ path: path.join(OUT, file), fullPage: full });
  console.log('ok', file);
}

(async () => {
  const browser = await chromium.launch();
  const admin = await login(browser, 'elastic', PASS);

  await shot(admin, `/app/dashboards#/view/applogs-all-dashboard?_g=(refreshInterval:(pause:!t,value:0),${T})`,
    'kibana-dashboard-all-projects.png', { full: true, wait: 15000 });

  await shot(admin, `/app/discover#/?_g=(${T})&_a=(columns:!(project,level,logger,message,exception.class),` +
    `dataSource:(dataViewId:applogs-all,type:dataView),query:(language:kuery,query:'level%20:%20ERROR'),sort:!(!('@timestamp',desc)))`,
    'kibana-discover-kql-filter.png');

  await shot(admin, `/app/discover#/?_g=(${T})&_a=(columns:!(project,level,message),` +
    `dataSource:(dataViewId:applogs-all,type:dataView),query:(language:kuery,query:'project%20:%20order-service%20and%20fields.stock%20%3C%2010'),sort:!(!('@timestamp',desc)))`,
    'kibana-discover-document.png', {
      before: async p => {
        const btn = await p.$('[data-test-subj="docTableExpandToggleColumn"]');
        if (btn) { await btn.click(); await p.waitForTimeout(3000); }
      }
    });

  await shot(admin, '/app/management/kibana/spaces', 'kibana-spaces.png', { wait: 6000 });
  await shot(admin, '/app/management/data/index_management/indices?filter=applogs', 'kibana-index-management.png', { wait: 8000 });
  await shot(admin, '/app/management/data/index_lifecycle_management/policies/edit/applogs-retention',
    'kibana-ilm-policy.png', { wait: 8000 });

  await shot(admin, `/app/monitoring#/overview?_g=(${T})`, 'kibana-stack-monitoring.png', {
    wait: 12000,
    before: async p => {
      const b = await p.$('button:has-text("Or, set up with self monitoring")');
      if (b) { await b.click(); await p.waitForTimeout(10000); }
    }
  });
  await shot(admin, `/app/monitoring#/elasticsearch?_g=(${T})`, 'kibana-monitoring-elasticsearch.png', { wait: 12000 });
  await shot(admin, `/app/monitoring#/logstash?_g=(${T})`, 'kibana-monitoring-logstash.png', { wait: 12000 });

  await shot(admin, '/app/dev_tools#/console', 'kibana-devtools-query.png', {
    wait: 8000,
    before: async p => {
      const skip = await p.$('button:has-text("Skip tour")');
      if (skip) { await skip.click(); await p.waitForTimeout(1500); }
      const editor = await p.$('.monaco-editor');
      if (!editor) return;
      await editor.click({ force: true });
      await p.keyboard.press('Control+A');
      await p.keyboard.press('Delete');
      // Satir satir yaz; Monaco otomatik kapatma/oneri penceresi icin her satirdan sonra Escape
      const lines = ['GET applogs-*/_search',
        '{"size": 0, "query": {"range": {"level_value": {"gte": 30}}},',
        '"aggs": {"proje": {"terms": {"field": "project"}, "aggs": {"seviye": {"terms": {"field": "level"}}}}}}'];
      for (const [i, line] of lines.entries()) {
        await p.keyboard.type(line, { delay: 10 });
        await p.keyboard.press('Escape');
        await p.keyboard.press('Shift+End');
        await p.keyboard.press('Delete');           // otomatik eklenen kapanis karakterlerini sil
        if (i < lines.length - 1) { await p.keyboard.press('Enter'); await p.keyboard.press('Shift+End'); await p.keyboard.press('Delete'); }
      }
      const run = await p.$('[data-test-subj="sendRequestButton"]');
      if (run) { await run.evaluate(el => el.click()); } else { await p.keyboard.press('Control+Enter'); }
      await p.waitForTimeout(5000);
    }
  });

  if (process.env.PROJECT_USER) {
    const proj = await login(browser, process.env.PROJECT_USER, process.env.PROJECT_PASS);
    await shot(proj, `/s/order-service/app/dashboards#/view/applogs-order-service-dashboard?_g=(refreshInterval:(pause:!t,value:0),${T})`,
      'kibana-dashboard-project-space.png', { full: true, wait: 15000 });
  }
  await browser.close();
})();
