import { build, transform } from 'esbuild';
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
const packageJson = JSON.parse(await readFile('package.json', 'utf8'));
const uuidManifest = JSON.parse(await readFile('preview/vendor/uuid/manifest.json', 'utf8'));
for (const asset of uuidManifest.assets) {
  const digest = createHash('sha256').update(await readFile('preview/vendor/uuid/' + asset.path)).digest('hex');
  if (digest !== asset.sha256) throw new Error('Pinned UUID asset checksum mismatch: ' + asset.path);
}
const packages = Object.keys(packageJson.dependencies).filter(name => !name.startsWith('@codemirror/') && !name.startsWith('@uiw/'));
packages.push('react/jsx-runtime', 'react/jsx-dev-runtime', 'react-dom/client');
const imports = packages.map((name, index) => `import * as dep${index} from ${JSON.stringify(name)};`).join('\n');
const values = packages.map((name, index) => `${JSON.stringify(name)}: Object.assign({__esModule: true}, dep${index}${name === 'react-router-dom' ? ', {BrowserRouter: dep'+index+'.MemoryRouter}' : ''})`).join(',\n');
const fallbacks = {};
for (const filename of await readdir('src/components/ui')) {
  if (filename.endsWith('.tsx')) fallbacks['src/components/ui/' + filename] = await readFile('src/components/ui/' + filename, 'utf8');
}
for (const path of ['src/lib/utils.ts', 'src/hooks/use-toast.ts', 'src/hooks/use-mobile.tsx']) fallbacks[path] = await readFile(path, 'utf8');
const source = `${imports}\nimport * as uuid from './vendor/uuid/uuid.js';\nconst __PREVIEW_MODULES__ = {${values}, uuid: Object.assign({__esModule: true}, uuid)};\nconst __PREVIEW_FALLBACKS__ = ${JSON.stringify(fallbacks)};\n` + await readFile('preview/runtime.ts', 'utf8');
await mkdir('public/preview/generated', { recursive: true });
await build({ stdin: { contents: source, resolveDir: process.cwd() + '/preview', loader: 'ts', sourcefile: 'runtime.ts' },
  outfile: 'public/preview/generated/runtime.js', bundle: true, minify: true, platform: 'browser', format: 'iife',
  define: { 'process.env.NODE_ENV': '"production"' }, legalComments: 'eof' });
const compiler = await transform(await readFile('node_modules/typescript/lib/typescript.js', 'utf8'), { minify: true, legalComments: 'eof' });
await writeFile('public/preview/generated/compiler.js', compiler.code);
const hash = createHash('sha256').update(await readFile('public/preview/generated/runtime.js')).update(compiler.code).digest('hex').slice(0, 16);
await writeFile('public/preview/index.html', (await readFile('preview/index.template.html', 'utf8')).replaceAll('BUILD_HASH', hash));
console.log('Built self-hosted sandbox preview runtime: ' + hash);
