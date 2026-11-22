// node screenshot-html.js in.html out.png  -> terminal penceresinin (.win) ekran goruntusu
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1400, height: 900 }, deviceScaleFactor: 2 });
  await p.goto('file://' + path.resolve(process.argv[2]));
  const el = await p.$('.win');
  await el.screenshot({ path: process.argv[3] });
  await b.close();
})();
