import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import assert from 'node:assert/strict';
const { chromium } = await import(process.env.CODEGEN_PLAYWRIGHT_PATH || 'playwright');
const root = resolve('public');
const csp = (await readFile('nginx.conf', 'utf8')).match(/location \^~ \/preview\/ \{\s*add_header Content-Security-Policy "([^"]+)"/)[1];
let forbiddenRequests = 0;
const fixture = {
  'index.html': '<div id="root"></div><script type="module" src="/src/main.tsx"></script>',
  'src/main.tsx': 'import React from "react"; import {createRoot} from "react-dom/client"; import App from "./App"; import "./index.css"; createRoot(document.getElementById("root")!).render(<App/>);',
  'src/index.css': '@import "tailwindcss"; @plugin "daisyui";',
  'src/App.tsx': 'import {useState} from "react"; import {Plus} from "lucide-react"; import {v4 as uuidv4, validate} from "uuid"; import {initialTasks} from "@/data"; export default function App(){const [tasks,setTasks]=useState<string[]>(()=>JSON.parse(localStorage.getItem("tasks")||JSON.stringify(initialTasks)));return <main className="p-8"><h1 className="text-3xl font-bold">Preview tasks</h1><button className="btn btn-primary" onClick={()=>{const id=uuidv4();if(!validate(id))throw new Error("Invalid UUID");const next=[...tasks,"Added task"];setTasks(next);localStorage.setItem("tasks",JSON.stringify(next))}}><Plus/>Add task</button>{tasks.map((task,i)=><p key={i}>{task}</p>)}</main>}',
  'src/data.ts': 'export const initialTasks: string[] = ["Verify preview"];',
};
const hostScript = `window.previewMessages=[];window.addEventListener('message',e=>{const f=document.querySelector('iframe');if(e.source!==f.contentWindow||e.origin!=='null')return;window.previewMessages.push(e.data);if(e.data.type==='codegen-preview-ready') f.contentWindow.postMessage({type:'codegen-preview-run',runId:'test',files:${JSON.stringify(fixture)}},'*')});`;
const server = createServer(async (req,res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/forbidden') { forbiddenRequests++; res.end('forbidden'); return; }
  if (url.pathname === '/') { res.setHeader('Content-Type','text/html'); res.end('<!doctype html><script src="/host.js"></script><iframe sandbox="allow-scripts" src="/preview/index.html" width="900" height="600"></iframe>'); return; }
  if (url.pathname === '/host.js') { res.setHeader('Content-Type','text/javascript');res.end(hostScript);return; }
  const path = resolve(root, '.' + url.pathname);
  if (!path.startsWith(root + '/')) {res.writeHead(404);res.end();return;}
  try {
    const bytes = await readFile(path); res.setHeader('Content-Type', ({'.js':'text/javascript','.html':'text/html','.css':'text/css'})[extname(path)] || 'text/plain');
    if (url.pathname.startsWith('/preview/')) res.setHeader('Content-Security-Policy',csp);
    res.end(bytes);
  } catch {res.writeHead(404);res.end();}
});
await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
const browser = await chromium.launch({executablePath: process.env.CODEGEN_CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true,chromiumSandbox:true});
try {
  const page = await browser.newPage(); const errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto(`http://127.0.0.1:${server.address().port}/`);
  const frame=page.frameLocator('iframe');
  try { await frame.getByRole('heading',{name:'Preview tasks'}).waitFor({timeout:30000}); }
  catch(error){console.log(JSON.stringify({errors,messages:await page.evaluate(()=>window.previewMessages)}));throw error;}
  await frame.getByRole('button',{name:'Add task'}).click();
  await frame.getByText('Added task',{exact:true}).waitFor();
  const sandboxFrame=page.frames().find(f=>f.url().includes('/preview/index.html'));
  const checks=await sandboxFrame.evaluate(async()=>{
    let parentBlocked=false;try {void parent.document.body;} catch {parentBlocked=true;}
    let networkBlocked=false;try {await fetch('/forbidden');} catch {networkBlocked=true;}
    return {parentBlocked,networkBlocked,sandboxStorage:localStorage.getItem('tasks')?.includes('Added task')};
  });
  assert.equal(checks.parentBlocked,true);assert.equal(checks.networkBlocked,true);assert.equal(forbiddenRequests,0);assert.equal(checks.sandboxStorage,true);
  await page.waitForTimeout(500);
  assert.equal(await frame.getByRole('heading').evaluate(e=>getComputedStyle(e).fontSize),'30px');
  console.log(JSON.stringify({passed:true,react_typescript_imports:true,interactive_state:true,uuid_task_creation:true,tailwind_daisyui:true,parent_and_network_isolated:true}));
  if(process.env.CODEGEN_PREVIEW_SCREENSHOT) await page.screenshot({path:process.env.CODEGEN_PREVIEW_SCREENSHOT});
} finally {await browser.close();await new Promise(resolve=>server.close(resolve));}
