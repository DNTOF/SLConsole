import puppeteer from '/tmp/pw/node_modules/puppeteer-core/lib/puppeteer/puppeteer-core.js';
import { spawn } from 'node:child_process';
import { mkdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const src = path.join(root, 'src');
const out = path.join(root, 'renders');
const only = process.argv.find((arg) => arg.startsWith('--only='));
const concepts = (only ? [only.slice('--only='.length)] : ['dust', 'matrix', 'caret', 'paper']);
const chrome = '/usr/local/bin/google-chrome';
const mode = process.argv.includes('--videos') ? 'videos' : 'stills';

const browser = await puppeteer.launch({
  executablePath: chrome,
  headless: 'new',
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--font-render-hinting=none', '--hide-scrollbars'],
});

async function shot(page, file) {
  await page.screenshot({ path: file, type: 'png' });
}

async function openPage(concept, theme, t) {
  const page = await browser.newPage();
  await page.setViewport({ width: 1080, height: 2400, deviceScaleFactor: 1 });
  const url = pathToFileURL(path.join(src, `${concept}.html`)).href + `?theme=${theme}` + (t != null ? `&t=${t}` : '');
  await page.goto(url, { waitUntil: 'load' });
  await page.waitForFunction('window.__fontsReady === true', { timeout: 20000 });
  if (t != null) await page.evaluate((time) => window.seek(time), t);
  return page;
}

if (mode === 'stills') {
  await mkdir(out, { recursive: true });
  for (const concept of concepts) {
    for (const theme of ['dark', 'light']) {
      const page = await openPage(concept, theme, 0.45);
      const file = path.join(out, `${concept}-${theme}.png`);
      await shot(page, file);
      await page.close();
      console.log('still', file);
    }
  }
} else {
  const fps = 24;
  const frames = Math.round(5.25 * fps);
  for (const concept of concepts) {
    const dir = path.join('/tmp', `navframes-${concept}`);
    await rm(dir, { recursive: true, force: true });
    await mkdir(dir, { recursive: true });
    const page = await openPage(concept, concept === 'paper' ? 'light' : 'dark', 0);
    for (let i = 0; i < frames; i++) {
      const t = i / fps;
      await page.evaluate((time) => window.seek(time), t);
      const file = path.join(dir, `f${String(i).padStart(4, '0')}.png`);
      await shot(page, file);
      if (i % 24 === 0) console.log(concept, i, '/', frames);
    }
    await page.close();
    const mp4 = path.join(out, `${concept}-preview.mp4`);
    await new Promise((resolve, reject) => {
      const ff = spawn('ffmpeg', [
        '-y', '-framerate', String(fps), '-i', path.join(dir, 'f%04d.png'),
        '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18', '-preset', 'veryfast',
        '-movflags', '+faststart', mp4,
      ], { stdio: 'inherit' });
      ff.on('exit', (code) => code === 0 ? resolve() : reject(new Error('ffmpeg ' + code)));
    });
    console.log('video', mp4);
  }
}

await browser.close();
