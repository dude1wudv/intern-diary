import json
import re
from datetime import date as date_type, datetime, timedelta, timezone
from typing import Any
from uuid import uuid4

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, HTMLResponse, PlainTextResponse

from .audit import audit
from .auth import UserContext, require_user
from .config import settings
from .db import conn
from .model_client import (
    assistant_chat,
    assistant_diary_preview,
    describe_image,
    generate_diary_json,
    generate_report_markdown,
    sort_day_text,
)
from .paths import day_dir, day_path, report_dir
from .schemas import (
    AssistantChatIn,
    DiaryEditConfirmIn,
    DiaryEditPreviewIn,
    GenerateIn,
    ReportGenerateIn,
    SortIn,
    TextEntryIn,
)
from .word_renderer import load_report_templates, render_docx, render_report_docx

app = FastAPI(title="Intern Diary")

_DEFAULT_REPORT_WORDS = {"weekly": 1000, "monthly": 1500, "internship_summary": 3000}

CONSOLE_HTML = r"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Diary Console</title>
  <style>
    :root {
      color-scheme: light;
      --bg:#f7f6f3; --panel:#ffffff; --panel-muted:#f2f0ec; --panel-hover:#ebe8e2;
      --ink:#232019; --ink-soft:#57524a; --muted:#8f887c;
      --border:#e4e1da; --border-strong:#d3cfc5;
      --accent:#b06a3f; --accent-strong:#8f5530; --accent-soft:#f1e6db;
      --ok:#3f7d52; --warn:#a5701f; --bad:#b23b30;
      --radius-sm:8px; --radius-md:10px; --radius-lg:14px;
      --mono:ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      --sans:-apple-system, BlinkMacSystemFont, "Segoe UI", Inter, ui-sans-serif, system-ui, sans-serif;
    }
    * { box-sizing:border-box; }
    html, body { height:100%; }
    body { margin:0; overflow:hidden; font-family:var(--sans); color:var(--ink); background:var(--bg); }
    button, input, textarea, select { font:inherit; }
    .app-shell { height:100vh; display:grid; grid-template-columns:288px minmax(0,1fr); }
    .sidebar { min-height:0; background:var(--panel); border-right:1px solid var(--border); padding:20px 18px; display:flex; flex-direction:column; gap:22px; overflow:auto; }
    .brand { display:flex; gap:10px; align-items:center; }
    .mark { width:28px; height:28px; border-radius:var(--radius-sm); display:grid; place-items:center; background:var(--panel-muted); border:1px solid var(--border); color:var(--ink-soft); font-size:13px; font-weight:600; }
    h1 { margin:0; font-size:16px; letter-spacing:-.01em; font-weight:600; }
    .subtitle { margin:1px 0 0; color:var(--muted); font-size:13px; }
    .section-title { color:var(--muted); font-size:12px; font-weight:600; letter-spacing:.05em; text-transform:uppercase; margin:0 0 10px; }
    .field { margin:0 0 10px; }
    .field:last-child { margin-bottom:0; }
    label { display:block; color:var(--muted); font-size:13px; margin:0 0 6px; }
    input, textarea, select {
      width:100%; border:1px solid var(--border); border-radius:var(--radius-sm); background:var(--panel);
      color:var(--ink); padding:8px 10px; outline:none; font-size:14px;
    }
    input:focus, textarea:focus, select:focus { border-color:var(--accent); box-shadow:0 0 0 3px var(--accent-soft); }
    textarea { min-height:76px; max-height:140px; resize:vertical; line-height:1.5; }
    .btn {
      border:1px solid var(--border); border-radius:var(--radius-sm); padding:7px 12px; cursor:pointer;
      color:var(--ink-soft); background:var(--panel); font-weight:500; font-size:14px;
      transition:background .12s ease, border-color .12s ease;
    }
    .btn:hover { background:var(--panel-hover); }
    .btn.primary { color:#fff; background:var(--accent-strong); border-color:var(--accent-strong); }
    .btn.primary:hover { background:var(--accent); }
    .btn.outline { color:var(--accent-strong); background:var(--panel); border-color:var(--border-strong); }
    .btn.outline:hover { background:var(--accent-soft); }
    .btn.block { width:100%; }
    .btn:disabled { opacity:.5; cursor:not-allowed; }
    .btn-row { display:flex; gap:8px; }
    .btn-row .btn { flex:1; }
    .link-btn {
      display:inline-flex; align-items:center; gap:6px; width:100%; justify-content:center;
      border:0; background:transparent; color:var(--accent-strong); font-size:13.5px; font-weight:500;
      padding:6px 0; cursor:pointer; border-top:1px solid var(--border); margin-top:8px; border-radius:0;
    }
    .link-btn:hover { color:var(--accent); }
    .status-list { border:1px solid var(--border); border-radius:var(--radius-md); overflow:hidden; }
    .status-row { display:flex; align-items:center; justify-content:space-between; padding:9px 10px; font-size:13.5px; border-bottom:1px solid var(--border); }
    .status-row:last-child { border-bottom:0; }
    .status-row .dot { width:6px; height:6px; border-radius:50%; background:var(--border-strong); display:inline-block; margin-right:7px; }
    .status-row.ok .dot { background:var(--ok); }
    .status-row .k { color:var(--ink-soft); display:flex; align-items:center; }
    .status-row .v { color:var(--muted); }
    .status-row.ok .v { color:var(--ok); }
    .message { min-height:16px; color:var(--muted); font-size:13px; line-height:1.4; margin:8px 0 0; }
    .workspace { min-height:0; display:grid; grid-template-rows:auto minmax(0,1fr); background:var(--bg); }
    .topbar { padding:14px 24px; border-bottom:1px solid var(--border); background:var(--panel); display:flex; align-items:center; justify-content:space-between; gap:14px; }
    .headline h2 { margin:0; font-size:18px; letter-spacing:-.01em; font-weight:600; }
    .tabs { display:flex; border:1px solid var(--border); border-radius:var(--radius-sm); overflow:hidden; }
    .tab {
      border:0; border-right:1px solid var(--border); background:transparent; color:var(--muted);
      padding:6px 13px; cursor:pointer; font-weight:500; font-size:13.5px; transition:background .12s ease, color .12s ease;
    }
    .tab:last-child { border-right:0; }
    .tab.active { color:var(--ink); background:var(--panel-muted); font-weight:600; }
    .tab:hover { background:var(--panel-hover); color:var(--ink); }
    .content-grid { min-height:0; padding:20px 26px; display:grid; grid-template-columns:minmax(0,1fr) 230px; gap:16px; }
    .preview-panel, .activity-panel { min-height:0; border:1px solid var(--border); border-radius:var(--radius-lg); background:var(--panel); overflow:hidden; box-shadow:0 1px 2px rgba(35,32,25,.03); }
    .panel-head { height:42px; padding:0 16px; display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid var(--border); }
    .panel-head strong { font-size:13.5px; color:var(--ink-soft); font-weight:600; }
    .panel-head span { color:var(--muted); font-size:12.5px; }
    .preview-scroll { height:calc(100vh - 130px); overflow:auto; padding:28px 32px; scroll-behavior:smooth; }
    .markdown-body { max-width:760px; margin:0 auto; line-height:1.65; font-size:16px; }
    .markdown-body h1, .markdown-body h2, .markdown-body h3 { font-weight:600; letter-spacing:-.01em; line-height:1.3; margin:1.3em 0 .55em; }
    .markdown-body h1, .markdown-body h2 { padding-bottom:.3em; border-bottom:1px solid var(--border); }
    .markdown-body h1 { font-size:25px; } .markdown-body h2 { font-size:20px; } .markdown-body h3 { font-size:17px; }
    .markdown-body p { margin:.75em 0; }
    .markdown-body ul { padding-left:1.3em; }
    .markdown-body li { margin:.3em 0; }
    .markdown-body code { font-family:var(--mono); font-size:.87em; background:var(--panel-muted); border:1px solid var(--border); padding:.15em .4em; border-radius:5px; }
    .markdown-body pre { overflow:auto; border:1px solid var(--border); border-radius:var(--radius-sm); padding:14px 16px; background:var(--panel-muted); }
    .markdown-body pre code { background:transparent; border:0; color:var(--ink-soft); padding:0; font-size:.9em; }
    .markdown-body blockquote { margin:1em 0; padding:.2em 1.1em; border-left:3px solid var(--border-strong); color:var(--ink-soft); }
    .empty-state { min-height:400px; display:grid; place-items:center; text-align:center; color:var(--muted); }
    .empty-state b { display:block; color:var(--ink-soft); font-size:15px; font-weight:600; margin-bottom:5px; }
    .activity-panel { display:grid; grid-template-rows:auto minmax(0,1fr); }
    .activity-list { overflow:auto; padding:4px 16px; }
    .activity-item { padding:11px 0; border-bottom:1px solid var(--border); }
    .activity-item:last-child { border-bottom:0; }
    .activity-item strong { display:block; font-size:13.5px; color:var(--ink-soft); font-weight:600; }
    .activity-item span { color:var(--muted); font-size:12.5px; }
    @media (max-width: 1060px) { .content-grid { grid-template-columns:1fr; } .activity-panel { display:none; } }
    @media (max-width: 820px) { body { overflow:auto; } .app-shell { height:auto; grid-template-columns:1fr; } .workspace { min-height:720px; } .topbar { align-items:flex-start; flex-direction:column; } .preview-scroll { height:58dvh; padding:18px; } }
    .assistant-view { min-height:0; padding:20px 26px; display:grid; grid-template-columns:220px minmax(0,1fr); gap:16px; }
    .assistant-sidebar { min-height:0; display:flex; flex-direction:column; gap:10px; border:1px solid var(--border); border-radius:var(--radius-lg); background:var(--panel); padding:14px; overflow:auto; }
    .session-list { display:flex; flex-direction:column; gap:6px; overflow:auto; }
    .session-item { border:1px solid transparent; border-radius:var(--radius-sm); padding:8px 9px; cursor:pointer; }
    .session-item:hover { background:var(--panel-hover); }
    .session-item.active { background:var(--accent-soft); border-color:var(--border-strong); }
    .session-item-title { font-size:13.5px; font-weight:600; color:var(--ink); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .session-item-preview { font-size:12px; color:var(--muted); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; margin-top:2px; }
    .session-empty { color:var(--muted); font-size:13px; text-align:center; padding:16px 0; }
    .assistant-panel { min-height:0; display:grid; grid-template-rows:auto auto minmax(0,1fr) auto auto; border:1px solid var(--border); border-radius:var(--radius-lg); background:var(--panel); overflow:hidden; }
    .assistant-header { display:flex; align-items:center; justify-content:space-between; padding:12px 16px; border-bottom:1px solid var(--border); }
    .session-title { font-size:14px; font-weight:600; }
    .assistant-header-actions { display:flex; gap:6px; }
    .edit-mode-bar { display:flex; align-items:center; gap:12px; padding:10px 16px; border-bottom:1px solid var(--border); flex-wrap:wrap; }
    .mode-pill {
      display:inline-flex; align-items:center; gap:6px; border:1px solid var(--border-strong); border-radius:999px;
      padding:6px 13px; background:var(--panel); color:var(--ink-soft); font-size:13.5px; font-weight:500; cursor:pointer;
      transition:background .12s ease, border-color .12s ease, color .12s ease;
    }
    .mode-pill:hover { background:var(--panel-hover); }
    .mode-pill .pill-dot { width:7px; height:7px; border-radius:50%; background:var(--border-strong); transition:background .12s ease; }
    .mode-pill.active { background:var(--accent-strong); border-color:var(--accent-strong); color:#fff; }
    .mode-pill.active .pill-dot { background:#fff; }
    .mode-pill:disabled { opacity:.5; cursor:not-allowed; }
    .edit-controls { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
    .edit-controls .date-field { display:flex; align-items:center; gap:6px; font-size:13px; color:var(--muted); }
    .edit-controls .date-field input { width:auto; padding:5px 8px; font-size:13px; }
    .target-chip {
      border:1px solid var(--border-strong); border-radius:999px; padding:5px 12px; background:var(--panel);
      color:var(--ink-soft); font-size:12.5px; font-weight:500; cursor:pointer; transition:background .12s ease, border-color .12s ease, color .12s ease;
    }
    .target-chip:hover { background:var(--panel-hover); }
    .target-chip.active { background:var(--accent-soft); border-color:var(--accent-strong); color:var(--accent-strong); }
    .target-chip:disabled { opacity:.5; cursor:not-allowed; }
    .assistant-messages { min-height:0; overflow:auto; padding:20px 24px; display:flex; flex-direction:column; gap:14px; }
    .assistant-empty { min-height:200px; display:grid; place-items:center; text-align:center; color:var(--muted); }
    .assistant-empty b { display:block; color:var(--ink-soft); font-size:15px; font-weight:600; margin-bottom:5px; }
    .msg-user { align-self:flex-end; margin-left:auto; max-width:80%; }
    .msg-assistant, .msg-preview { max-width:88%; }
    .msg-bubble { border:1px solid var(--border); border-radius:var(--radius-md); padding:10px 14px; font-size:14.5px; line-height:1.6; background:var(--panel-muted); white-space:pre-wrap; }
    .msg-user .msg-bubble { background:var(--accent-soft); border-color:var(--border-strong); }
    .msg-system { text-align:center; color:var(--muted); font-size:12.5px; margin:4px 0; }
    .msg-actions { display:flex; gap:8px; margin-top:6px; }
    .msg-action { border:0; background:transparent; color:var(--muted); font-size:12.5px; cursor:pointer; padding:2px 0; }
    .msg-action:hover { color:var(--accent-strong); }
    .preview-status { font-size:12px; color:var(--muted); margin-top:6px; }
    .status-confirmed .preview-status { color:var(--ok); }
    .status-cancelled .preview-status { color:var(--bad); }
    .change-card { border:1px solid var(--border); border-radius:var(--radius-sm); margin-top:8px; padding:8px 10px; background:var(--panel); }
    .change-card summary { cursor:pointer; font-size:13px; color:var(--ink-soft); font-weight:500; }
    .change-diff { display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:8px; }
    .change-label { font-size:11px; color:var(--muted); margin-bottom:4px; text-transform:uppercase; letter-spacing:.04em; }
    .change-diff pre { max-height:220px; overflow:auto; border:1px solid var(--border); border-radius:var(--radius-sm); padding:8px 10px; background:var(--panel-muted); font-size:12.5px; white-space:pre-wrap; }
    .assistant-composer { display:flex; gap:10px; align-items:flex-end; padding:14px 16px; border-top:1px solid var(--border); }
    .assistant-composer textarea { flex:1; min-height:44px; max-height:140px; }
    @media (max-width: 1060px) { .assistant-view { grid-template-columns:1fr; } .assistant-sidebar { display:none; } }
  </style>
</head>
<body>
<div class="app-shell">
  <aside class="sidebar">
    <div class="brand">
      <div class="mark">D</div>
      <div><h1>Diary Console</h1><p class="subtitle">daily workspace</p></div>
    </div>

    <section>
      <div class="section-title">Session</div>
      <div class="field"><label>API Token</label><input id="token" type="password" placeholder="粘贴现有 API Token"></div>
      <div class="btn-row"><button class="btn" onclick="saveToken()">保存</button><button class="btn" onclick="clearToken()">清除</button></div>
    </section>

    <section>
      <div class="section-title">Day</div>
      <div class="field"><label>日期</label><input id="date" type="date"></div>
      <button class="btn block" onclick="refreshAll()">刷新当天状态</button>
      <div class="status-list" id="status" style="margin-top:10px"></div>
    </section>

    <section>
      <div class="section-title">Command</div>
      <div class="field"><label>额外指令</label><textarea id="instruction" placeholder="例如：突出今天的学习收获，语气更正式"></textarea></div>
      <div class="btn-row">
        <button class="btn outline" id="sortBtn" onclick="runAction('sort')">整理素材</button>
        <button class="btn primary" id="genBtn" onclick="runAction('generate')">生成日记</button>
      </div>
      <button class="link-btn" onclick="downloadDocx()">↓ 下载 Word</button>
      <p class="message" id="msg"></p>
    </section>

    <section>
      <div class="section-title">Report</div>
      <div class="field"><label>报告类型</label><select id="reportType" onchange="paintReportTemplates()"><option value="weekly">周报</option><option value="monthly">月报</option><option value="internship_summary">实习总结</option></select></div>
      <div class="field"><label>开始日期</label><input id="reportStart" type="date"></div>
      <div class="field"><label>结束日期</label><input id="reportEnd" type="date"></div>
      <div class="field"><label>模板</label><select id="reportTemplate"></select></div>
      <button class="btn primary block" id="reportBtn" onclick="generateReport()">生成报告</button>
      <div class="btn-row" style="margin-top:8px">
        <button class="btn outline" onclick="downloadReport('draft')">Markdown</button>
        <button class="btn outline" onclick="downloadReport('docx')">Word</button>
      </div>
    </section>
  </aside>

  <main class="workspace">
    <header class="topbar">
      <div class="headline"><h2 id="viewTitle">原始记录</h2></div>
      <nav class="tabs" aria-label="content tabs">
        <button class="tab active" data-tab="raw-text" onclick="loadText('raw-text', this)">原始记录</button>
        <button class="tab" data-tab="images" onclick="loadImages(this)">图片描述</button>
        <button class="tab" data-tab="sorted-notes" onclick="loadText('sorted-notes', this)">整理稿</button>
        <button class="tab" data-tab="draft" onclick="loadText('draft', this)">草稿</button>
        <button class="tab" data-tab="assistant" onclick="showAssistantTab(this)">AI 助手</button>
      </nav>
    </header>

    <section class="content-grid" id="previewView">
      <article class="preview-panel">
        <div class="panel-head"><strong id="panelTitle">Markdown Preview</strong><span id="panelMeta">ready</span></div>
        <div class="preview-scroll"><div id="content" class="markdown-body"><div class="empty-state"><div><b>选择日期后刷新</b><span>内容会以 Markdown 样式渲染在这里。</span></div></div></div></div>
      </article>
      <aside class="activity-panel">
        <div class="panel-head"><strong>今日流程</strong><span>status</span></div>
        <div class="activity-list" id="activity">
          <div class="activity-item"><strong>1. 收集素材</strong><span>手机端上传文字和图片。</span></div>
          <div class="activity-item"><strong>2. 整理素材</strong><span>生成 sorted_notes.md。</span></div>
          <div class="activity-item"><strong>3. 生成日记</strong><span>输出草稿和 Word。</span></div>
        </div>
      </aside>
    </section>

    <section class="assistant-view" id="assistantView" style="display:none">
      <aside class="assistant-sidebar">
        <button class="btn primary block" onclick="createNewSession()">+ 新建对话</button>
        <input id="sessionSearch" type="text" placeholder="搜索会话" oninput="renderSessionList()">
        <div class="session-list" id="sessionList"></div>
      </aside>
      <div class="assistant-panel">
        <div class="assistant-header">
          <span class="session-title" id="sessionTitleLabel">新对话</span>
          <div class="assistant-header-actions">
            <button class="btn" onclick="renameCurrentSession()">重命名</button>
            <button class="btn" onclick="deleteCurrentSession()">删除</button>
          </div>
        </div>
        <div class="edit-mode-bar">
          <button type="button" class="mode-pill" id="editModeToggle" onclick="onEditModeToggle()" aria-pressed="false">
            <span class="pill-dot"></span>日记修改模式
          </button>
          <div class="edit-controls" id="editControls" style="display:none">
            <span class="date-field">正在修改 <input type="date" id="editDate"></span>
            <button type="button" class="target-chip active" id="targetDraft" onclick="toggleTargetChip(this)">草稿</button>
            <button type="button" class="target-chip" id="targetSorted" onclick="toggleTargetChip(this)">整理稿</button>
            <button type="button" class="target-chip" id="targetImages" onclick="toggleTargetChip(this)">图片说明</button>
          </div>
        </div>
        <div class="assistant-messages" id="assistantMessages"></div>
        <p class="message" id="assistantMsg"></p>
        <div class="assistant-composer">
          <textarea id="assistantInput" placeholder="给 AI 助手发消息…" onkeydown="onAssistantInputKeydown(event)"></textarea>
          <button class="btn primary" id="assistantSendBtn" onclick="sendAssistantMessage()">发送</button>
        </div>
      </div>
    </section>
  </main>
</div>
<script>
const $ = (id) => document.getElementById(id);
const today = new Date().toISOString().slice(0, 10);
const titles = {"raw-text":"原始记录", "sorted-notes":"整理稿", "draft":"日记草稿", "images":"图片描述"};
$("date").value = today;
$("reportStart").value = today;
$("reportEnd").value = today;
$("token").value = localStorage.getItem("diaryConsoleToken") || "";
let reportTemplates = [];
let lastReportId = "";
function token(){ return $("token").value.trim(); }
function headers(json=false){ const h = {Authorization: `Bearer ${token()}`}; if(json) h["Content-Type"]="application/json"; return h; }
function msg(t){ $("msg").textContent = t || ""; }
function setMeta(t){ $("panelMeta").textContent = t || "ready"; }
function saveToken(){ localStorage.setItem("diaryConsoleToken", token()); refreshAll(); loadReportTemplates(); }
function clearToken(){ localStorage.removeItem("diaryConsoleToken"); $("token").value=""; msg("Token 已清除"); }
function setActive(btn, key){ document.querySelectorAll('.tab').forEach(b=>b.classList.toggle('active', b===btn || b.dataset.tab===key)); $("viewTitle").textContent = titles[key] || "内容预览"; $("previewView").style.display = "grid"; $("assistantView").style.display = "none"; }
function escapeHtml(s){ return (s||"").replace(/[&<>]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;"}[c])); }
function inlineMarkdown(s){ return escapeHtml(s).replace(/`([^`]+)`/g,'<code>$1</code>').replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>'); }
// content.innerHTML is updated by the preview loaders below.
function renderMarkdown(md){
  if(!md || !md.trim()) return '<div class="empty-state"><div><b>暂无内容</b><span>这个日期还没有生成对应文件。</span></div></div>';
  const lines = md.replace(/\r\n/g,'\n').split('\n');
  let html='', list=false, code=false, buf=[];
  const para = () => { if(buf.length){ html += `<p>${inlineMarkdown(buf.join(' '))}</p>`; buf=[]; } };
  for(const line of lines){
    if(line.trim().startsWith('```')){ para(); if(!code){ code=true; html+='<pre><code>'; } else { code=false; html+='</code></pre>'; } continue; }
    if(code){ html += escapeHtml(line)+'\n'; continue; }
    if(/^#{1,3}\s+/.test(line)){ para(); if(list){ html+='</ul>'; list=false; } const n=line.match(/^#+/)[0].length; html += `<h${n}>${inlineMarkdown(line.replace(/^#{1,3}\s+/,''))}</h${n}>`; continue; }
    if(/^[-*]\s+/.test(line)){ para(); if(!list){ html+='<ul>'; list=true; } html += `<li>${inlineMarkdown(line.replace(/^[-*]\s+/,''))}</li>`; continue; }
    if(/^>\s?/.test(line)){ para(); if(list){ html+='</ul>'; list=false; } html += `<blockquote>${inlineMarkdown(line.replace(/^>\s?/,''))}</blockquote>`; continue; }
    if(!line.trim()){ para(); if(list){ html+='</ul>'; list=false; } continue; }
    buf.push(line.trim());
  }
  para(); if(list) html+='</ul>'; if(code) html+='</code></pre>';
  return html;
}
async function api(path, opts={}) {
  const r = await fetch(path, opts);
  if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
  return r;
}
function paintStatus(state){
  const items = [
    ["原始记录", state.raw_text_exists ? "有" : "无", state.raw_text_exists],
    ["图片描述", `${state.described_image_count}/${state.image_count}`, state.image_count > 0],
    ["整理稿", state.sorted_notes_exists ? "有" : "无", state.sorted_notes_exists],
    ["日记草稿", state.diary_draft_exists ? "有" : "无", state.diary_draft_exists],
    ["Word", state.diary_final_exists ? "有" : "无", state.diary_final_exists],
  ];
  $("status").innerHTML = items.map(([k,v,ok]) => `<div class="status-row ${ok?'ok':''}"><span class="k"><span class="dot"></span>${k}</span><span class="v">${v}</span></div>`).join("");
}
async function refreshAll(){
  try { const d = $("date").value || today; const state = await (await api(`/api/days/${d}`, {headers: headers()})).json(); paintStatus(state); msg("已刷新"); }
  catch(e) { msg(e.message); }
}
async function loadText(kind, btn){
  setActive(btn, kind); setMeta("loading");
  try { const d = $("date").value || today; const text = await (await api(`/api/days/${d}/${kind}`, {headers: headers()})).text(); $("content").innerHTML = renderMarkdown(text); setMeta(`${text.length} chars`); }
  catch(e) { $("content").innerHTML = renderMarkdown(`> 暂无内容或读取失败。\n\n${e.message}`); setMeta("error"); }
}
async function loadImages(btn){
  setActive(btn, "images"); setMeta("loading");
  try { const d = $("date").value || today; const data = await (await api(`/api/days/${d}/image-descriptions`, {headers: headers()})).json(); const md = Object.entries(data).map(([k,v]) => `## ${k}\n\n${v}`).join("\n\n---\n\n"); $("content").innerHTML = renderMarkdown(md || ""); setMeta(`${Object.keys(data).length} images`); }
  catch(e) { $("content").innerHTML = renderMarkdown(`> 暂无图片描述或读取失败。\n\n${e.message}`); setMeta("error"); }
}
async function runAction(kind){
  const btn = kind === "sort" ? $("sortBtn") : $("genBtn"); btn.disabled = true; msg("处理中...");
  try { const d = $("date").value || today; const body = {date:d, extra_instruction:$("instruction").value}; if (kind === "generate") body.word_count = 800; await api(kind === "sort" ? "/api/actions/sort-day" : "/api/actions/generate-diary", {method:"POST", headers:headers(true), body:JSON.stringify(body)}); await refreshAll(); await loadText(kind === "sort" ? "sorted-notes" : "draft"); }
  catch(e) { msg(e.message); } finally { btn.disabled = false; }
}
async function downloadDocx(){
  try { const d = $("date").value || today; const blob = await (await api(`/api/days/${d}/files/diary_final.docx`, {headers:headers()})).blob(); const url = URL.createObjectURL(blob), a = document.createElement("a"); a.href = url; a.download = `diary_final_${d}.docx`; a.click(); URL.revokeObjectURL(url); }
  catch(e) { msg(e.message); }
}
function paintReportTemplates(){
  const type = $("reportType").value;
  const items = reportTemplates.filter(t => t.type === type);
  $("reportTemplate").innerHTML = '<option value="">默认模板</option>' + items.map(t => `<option value="${escapeHtml(t.id)}">${escapeHtml(t.name || t.id)}</option>`).join("");
}
async function loadReportTemplates(){
  try { reportTemplates = (await (await api("/api/report-templates", {headers:headers()})).json()).templates || []; paintReportTemplates(); }
  catch(e) { reportTemplates = []; paintReportTemplates(); }
}
function downloadBlob(blob, name){ const url = URL.createObjectURL(blob), a = document.createElement("a"); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url); }
async function generateReport(){
  $("reportBtn").disabled = true; msg("报告生成中...");
  try {
    const body = {type:$("reportType").value, start_date:$("reportStart").value || today, end_date:$("reportEnd").value || today, extra_instruction:$("instruction").value};
    if ($("reportTemplate").value) body.template_id = $("reportTemplate").value;
    const data = await (await api("/api/actions/generate-report", {method:"POST", headers:headers(true), body:JSON.stringify(body)})).json();
    lastReportId = data.report_id;
    $("previewView").style.display = "grid"; $("assistantView").style.display = "none"; $("viewTitle").textContent = "报告草稿"; $("panelTitle").textContent = "Report Preview";
    $("content").innerHTML = renderMarkdown(data.markdown || ""); setMeta(lastReportId); msg("报告已生成，可下载 Markdown / Word");
  } catch(e) { msg(e.message); } finally { $("reportBtn").disabled = false; }
}
async function downloadReport(kind){
  try {
    if (!lastReportId) throw new Error("请先生成报告");
    const path = kind === "docx" ? `/api/reports/${lastReportId}/files/report.docx` : `/api/reports/${lastReportId}/draft`;
    const ext = kind === "docx" ? "docx" : "md";
    downloadBlob(await (await api(path, {headers:headers()})).blob(), `${lastReportId}.${ext}`);
  } catch(e) { msg(e.message); }
}
refreshAll();
if (token()) loadReportTemplates();

// ---- AI 助手 Tab ----
const ASSISTANT_STORE_KEY = "diaryAssistantStore";
let assistantBusy = false;

function loadAssistantStore(){
  try {
    const raw = localStorage.getItem(ASSISTANT_STORE_KEY);
    if (raw) { const store = JSON.parse(raw); if (store && Array.isArray(store.sessions)) return store; }
  } catch(e) {}
  return { sessions: [], currentSessionId: null };
}
function saveAssistantStore(store){ localStorage.setItem(ASSISTANT_STORE_KEY, JSON.stringify(store)); }
function nowLabel(){ const d = new Date(); return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`; }
function newSessionObj(){ const id = String(Date.now()) + "-" + Math.random().toString(36).slice(2,8); const ts = Date.now(); return { id, title: "新对话", createdAt: ts, updatedAt: ts, messages: [] }; }
function currentSession(store){ return store.sessions.find(s => s.id === store.currentSessionId) || null; }
function ensureCurrentSession(store){
  let s = currentSession(store);
  if (!s) { s = newSessionObj(); store.sessions.unshift(s); store.currentSessionId = s.id; saveAssistantStore(store); }
  return s;
}
function sessionPreview(s){ const firstUser = s.messages.find(m => m.type === "user"); return (s.title !== "新对话" ? s.title : (firstUser ? firstUser.content.slice(0,40) : "空对话")); }

function showAssistantTab(btn){
  document.querySelectorAll('.tab').forEach(b=>b.classList.toggle('active', b===btn));
  $("viewTitle").textContent = "AI 助手";
  $("previewView").style.display = "none";
  $("assistantView").style.display = "grid";
  if (!$("editDate").value) $("editDate").value = today;
  renderSessionList();
  renderCurrentSession();
}

function renderSessionList(){
  const store = loadAssistantStore();
  ensureCurrentSession(store);
  const q = ($("sessionSearch").value || "").trim().toLowerCase();
  const sorted = store.sessions.slice().sort((a,b) => b.updatedAt - a.updatedAt);
  const filtered = q ? sorted.filter(s => (s.title||"").toLowerCase().includes(q) || sessionPreview(s).toLowerCase().includes(q)) : sorted;
  if (!filtered.length) { $("sessionList").innerHTML = '<div class="session-empty">没有匹配的会话</div>'; return; }
  $("sessionList").innerHTML = filtered.map(s => `
    <div class="session-item ${s.id===store.currentSessionId?'active':''}" onclick="selectSession('${s.id}')">
      <div class="session-item-title">${escapeHtml(s.title)}</div>
      <div class="session-item-preview">${escapeHtml(sessionPreview(s))}</div>
    </div>`).join("");
}

function selectSession(id){
  const store = loadAssistantStore();
  if (!store.sessions.some(s => s.id === id)) return;
  store.currentSessionId = id;
  saveAssistantStore(store);
  renderSessionList();
  renderCurrentSession();
}

function createNewSession(){
  if (assistantBusy) return;
  const store = loadAssistantStore();
  const s = newSessionObj();
  store.sessions.unshift(s);
  store.currentSessionId = s.id;
  saveAssistantStore(store);
  renderSessionList();
  renderCurrentSession();
}

function renameCurrentSession(){
  const store = loadAssistantStore();
  const s = ensureCurrentSession(store);
  const title = prompt("重命名会话", s.title === "新对话" ? "" : s.title);
  if (title === null) return;
  const trimmed = title.trim();
  if (trimmed) s.title = trimmed;
  s.updatedAt = Date.now();
  saveAssistantStore(store);
  renderSessionList();
  renderCurrentSession();
}

function deleteCurrentSession(){
  const store = loadAssistantStore();
  const s = currentSession(store);
  if (!s) return;
  if (!confirm("删除这个会话？此操作不可撤销。")) return;
  store.sessions = store.sessions.filter(x => x.id !== s.id);
  store.currentSessionId = null;
  if (!store.sessions.length) { const fresh = newSessionObj(); store.sessions.push(fresh); store.currentSessionId = fresh.id; }
  else { store.currentSessionId = store.sessions.slice().sort((a,b)=>b.updatedAt-a.updatedAt)[0].id; }
  saveAssistantStore(store);
  renderSessionList();
  renderCurrentSession();
}

function assistantMsg(t){ $("assistantMsg").textContent = t || ""; }

function setAssistantBusy(busy){
  assistantBusy = busy;
  $("assistantSendBtn").disabled = busy;
  $("assistantInput").disabled = busy;
  $("editModeToggle").disabled = busy;
  document.querySelectorAll(".target-chip").forEach(el => el.disabled = busy);
  assistantMsg(busy ? "AI 正在回复…" : "");
}

function isEditModeOn(){ return $("editModeToggle").classList.contains("active"); }

function onEditModeToggle(){
  if (assistantBusy) return;
  const nowOn = !isEditModeOn();
  $("editModeToggle").classList.toggle("active", nowOn);
  $("editModeToggle").setAttribute("aria-pressed", String(nowOn));
  $("editControls").style.display = nowOn ? "flex" : "none";
}

function toggleTargetChip(el){
  if (assistantBusy) return;
  el.classList.toggle("active");
}

function onAssistantInputKeydown(e){
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendAssistantMessage(); }
}

function renderCurrentSession(){
  const store = loadAssistantStore();
  const s = ensureCurrentSession(store);
  $("sessionTitleLabel").textContent = s.title;
  const box = $("assistantMessages");
  if (!s.messages.length) {
    box.innerHTML = '<div class="assistant-empty"><div><b>开始对话</b><span>向 AI 助手提问，或打开日记修改模式生成修改预览。</span></div></div>';
    return;
  }
  box.innerHTML = s.messages.map(m => renderAssistantMessage(m)).join("");
}

function renderAssistantMessage(m){
  if (m.type === "system") return `<div class="msg-system">${escapeHtml(m.content)}</div>`;
  if (m.type === "user") {
    return `<div class="msg-user"><div class="msg-bubble">${escapeHtml(m.content)}</div></div>`;
  }
  if (m.type === "assistant") {
    return `<div class="msg-assistant">
      <div class="msg-bubble">${escapeHtml(m.content)}</div>
      <div class="msg-actions">
        <button class="msg-action" onclick="copyAssistantMessage(${m.id})">复制</button>
        <button class="msg-action" onclick="deleteAssistantMessage(${m.id})">删除</button>
        <button class="msg-action" onclick="regenerateAssistantMessage(${m.id})">重新生成</button>
      </div>
    </div>`;
  }
  if (m.type === "preview") {
    const statusClass = m.status === "confirmed" ? "status-confirmed" : (m.status === "cancelled" ? "status-cancelled" : "");
    const statusText = m.status === "confirmed" ? "已写回" : (m.status === "cancelled" ? "已取消" : "待确认");
    const changes = (m.changes || []).map((c, i) => `
      <details class="change-card">
        <summary>${escapeHtml(c.target)} · ${escapeHtml(c.after_summary || "")}</summary>
        <div class="change-diff">
          <div><div class="change-label">修改前</div><pre>${escapeHtml(c.before_content || "")}</pre></div>
          <div><div class="change-label">修改后</div><pre>${escapeHtml(c.new_content || "")}</pre></div>
        </div>
      </details>`).join("");
    const actions = m.status === "pending" ? `
      <div class="msg-actions">
        <button class="msg-action" onclick="confirmDiaryPreview(${m.id})">确认写回</button>
        <button class="msg-action" onclick="cancelDiaryPreview(${m.id})">取消</button>
        <button class="msg-action" onclick="deleteAssistantMessage(${m.id})">删除</button>
      </div>` : `<div class="msg-actions"><button class="msg-action" onclick="deleteAssistantMessage(${m.id})">删除</button></div>`;
    return `<div class="msg-preview ${statusClass}">
      <div class="msg-bubble">${escapeHtml(m.reply || "")}</div>
      ${changes}
      <div class="preview-status">${statusText}</div>
      ${actions}
    </div>`;
  }
  return "";
}

function appendAssistantMessage(session, message){
  session.messages.push(message);
  session.updatedAt = Date.now();
  if (session.title === "新对话" && message.type === "user") session.title = message.content.slice(0, 40);
  const store = loadAssistantStore();
  const idx = store.sessions.findIndex(s => s.id === session.id);
  if (idx >= 0) store.sessions[idx] = session; else store.sessions.unshift(session);
  saveAssistantStore(store);
}

function persistSession(session){
  session.updatedAt = Date.now();
  const store = loadAssistantStore();
  const idx = store.sessions.findIndex(s => s.id === session.id);
  if (idx >= 0) store.sessions[idx] = session;
  saveAssistantStore(store);
}

function nextMessageId(session){ return (session.messages.reduce((max, m) => Math.max(max, m.id || 0), 0)) + 1; }

function textTurns(session){
  return session.messages.filter(m => m.type === "user" || m.type === "assistant").map(m => ({ role: m.type, content: m.content }));
}

function selectedEditTargets(){
  const out = [];
  if ($("targetDraft").classList.contains("active")) out.push("diary_draft.md");
  if ($("targetSorted").classList.contains("active")) out.push("sorted_notes.md");
  if ($("targetImages").classList.contains("active")) out.push("image_descriptions");
  return out;
}

async function sendAssistantMessage(){
  if (assistantBusy) return;
  const text = $("assistantInput").value.trim();
  if (!text) return;
  const store = loadAssistantStore();
  const session = ensureCurrentSession(store);
  $("assistantInput").value = "";
  appendAssistantMessage(session, { id: nextMessageId(session), type: "user", time: nowLabel(), content: text });
  renderSessionList();
  renderCurrentSession();
  if (isEditModeOn()) {
    await sendDiaryEditInstruction(session, text);
  } else {
    await sendPlainChat(session, text);
  }
}

async function sendPlainChat(session, userText){
  setAssistantBusy(true);
  try {
    const history = textTurns(session).slice(-20);
    const resp = await fetch("/api/assistant/chat", { method: "POST", headers: headers(true), body: JSON.stringify({ messages: history }) });
    if (!resp.ok) throw new Error(`${resp.status} ${await resp.text()}`);
    const data = await resp.json();
    appendAssistantMessage(session, { id: nextMessageId(session), type: "assistant", time: nowLabel(), content: data.reply || "" });
    renderSessionList();
    renderCurrentSession();
  } catch(e) {
    assistantMsg(`发送失败：${e.message}`);
  } finally {
    setAssistantBusy(false);
  }
}

async function sendDiaryEditInstruction(session, instruction){
  setAssistantBusy(true);
  try {
    const turns = textTurns(session);
    const history = turns.slice(0, Math.max(0, turns.length - 1)).slice(-20);
    const date = $("editDate").value || today;
    const targets = selectedEditTargets();
    const resp = await fetch("/api/assistant/diary-edit/preview", {
      method: "POST", headers: headers(true),
      body: JSON.stringify({ date, instruction, messages: history, targets }),
    });
    if (!resp.ok) throw new Error(`${resp.status} ${await resp.text()}`);
    const data = await resp.json();
    appendAssistantMessage(session, {
      id: nextMessageId(session), type: "preview", time: nowLabel(),
      reply: data.reply || "", previewId: data.preview_id, changes: data.changes || [], status: "pending",
    });
    renderSessionList();
    renderCurrentSession();
  } catch(e) {
    assistantMsg(`生成修改预览失败：${e.message}`);
  } finally {
    setAssistantBusy(false);
  }
}

function findMessage(session, id){ return session.messages.find(m => m.id === id); }

async function confirmDiaryPreview(id){
  if (assistantBusy) return;
  const store = loadAssistantStore();
  const session = ensureCurrentSession(store);
  const m = findMessage(session, id);
  if (!m || m.status !== "pending") return;
  setAssistantBusy(true);
  try {
    const resp = await fetch("/api/assistant/diary-edit/confirm", { method: "POST", headers: headers(true), body: JSON.stringify({ preview_id: m.previewId }) });
    if (!resp.ok) {
      if (resp.status === 404) throw new Error("预览已过期，请重新生成修改预览");
      throw new Error(`${resp.status} ${await resp.text()}`);
    }
    m.status = "confirmed";
    persistSession(session);
    renderSessionList();
    renderCurrentSession();
  } catch(e) {
    assistantMsg(`确认写回失败：${e.message}`);
  } finally {
    setAssistantBusy(false);
  }
}

function cancelDiaryPreview(id){
  const store = loadAssistantStore();
  const session = ensureCurrentSession(store);
  const m = findMessage(session, id);
  if (!m || m.status !== "pending") return;
  m.status = "cancelled";
  persistSession(session);
  renderSessionList();
  renderCurrentSession();
}

function copyAssistantMessage(id){
  const store = loadAssistantStore();
  const session = ensureCurrentSession(store);
  const m = findMessage(session, id);
  if (!m) return;
  navigator.clipboard.writeText(m.content || "").then(
    () => assistantMsg("已复制"),
    () => assistantMsg("复制失败，浏览器拒绝了剪贴板权限"),
  );
}

function deleteAssistantMessage(id){
  const store = loadAssistantStore();
  const session = ensureCurrentSession(store);
  session.messages = session.messages.filter(m => m.id !== id);
  persistSession(session);
  renderSessionList();
  renderCurrentSession();
}

async function regenerateAssistantMessage(id){
  if (assistantBusy) return;
  const store = loadAssistantStore();
  const session = ensureCurrentSession(store);
  const index = session.messages.findIndex(m => m.id === id);
  if (index < 0) return;
  let previousUser = null;
  for (let i = index - 1; i >= 0; i--) { if (session.messages[i].type === "user") { previousUser = session.messages[i]; break; } }
  if (!previousUser) return;
  session.messages = session.messages.filter(m => m.id !== id);
  persistSession(session);
  renderSessionList();
  renderCurrentSession();
  await sendPlainChat(session, previousUser.content);
}
</script>
</body>
</html>"""


# Diary-edit write targets are restricted to today's editable working files.
# raw_text.md, images/*, diary_final.docx are never reachable through this set.
_ALLOWED_EDIT_TARGETS = {"sorted_notes.md", "diary_draft.md"}
_ALLOWED_IMAGE_DESC_RE = re.compile(r"^image_descriptions/[A-Za-z0-9_-]+\.md$")
_UPLOAD_CHUNK_BYTES = 1024 * 1024

# In-memory preview store: preview_id -> {workspace, date, changes}. Previews
# are short-lived (confirm-or-discard within the same session), so losing them
# on restart is acceptable and avoids a schema change for ephemeral data.
_diary_edit_previews: dict[str, dict[str, Any]] = {}


def _is_allowed_edit_target(target: str) -> bool:
    if target in _ALLOWED_EDIT_TARGETS:
        return True
    return bool(_ALLOWED_IMAGE_DESC_RE.fullmatch(target))


def _target_requested(target: str, requested: list[str]) -> bool:
    if not requested:
        return True
    if target in requested:
        return True
    return target.startswith("image_descriptions/") and "image_descriptions" in requested


async def _save_upload_limited(upload: UploadFile, target, max_bytes: int) -> None:
    total = 0
    try:
        with target.open("wb") as f:
            while chunk := await upload.read(_UPLOAD_CHUNK_BYTES):
                total += len(chunk)
                if total > max_bytes:
                    raise HTTPException(status_code=413, detail="image too large")
                f.write(chunk)
    except HTTPException:
        target.unlink(missing_ok=True)
        raise


@app.get("/console")
def console():
    return HTMLResponse(CONSOLE_HTML)


@app.get("/health")
def health():
    return {"ok": True}


@app.get("/api/me", )
def me(user: UserContext = Depends(require_user)):
    return {"ok": True, "workspace": user.workspace, "profile": {"name": user.name, "class": user.class_name, "student_id": user.student_id}}


@app.post("/api/entries/text", )
def add_text(body: TextEntryIn, user: UserContext = Depends(require_user)):
    now = datetime.now(timezone.utc).isoformat()
    entry_id = "entry_" + uuid4().hex[:12]
    with (day_dir(body.date, user) / "raw_text.md").open("a", encoding="utf-8") as f:
        f.write(f"\n## {now}\n\n{body.content}\n")
    with conn(user) as c:
        c.execute(
            "insert into entries(id,date,type,created_at,content,status,exclude_from_diary) values(?,?,?,?,?,?,?)",
            (entry_id, body.date, "text", now, body.content, "stored", int(body.exclude_from_diary)),
        )
    audit(body.date, user, "text.add", entry_id)
    return {"id": entry_id, "status": "stored"}


@app.get("/api/days/{date}", )
def day_status(date: str, user: UserContext = Depends(require_user)):
    try:
        p = day_dir(date, user)
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")
    with conn(user) as c:
        rows = c.execute("select type,status from entries where date=?", (date,)).fetchall()
    return {
        "date": date,
        "raw_text_exists": (p / "raw_text.md").exists(),
        "image_count": sum(1 for r in rows if r["type"] == "image"),
        "described_image_count": sum(1 for r in rows if r["type"] == "image" and r["status"] == "described"),
        "sorted_notes_exists": (p / "sorted_notes.md").exists(),
        "diary_draft_exists": (p / "diary_draft.md").exists(),
        "diary_final_exists": (p / "diary_final.docx").exists(),
    }


def _day_text_file(date: str, filename: str, user: UserContext) -> PlainTextResponse:
    try:
        f = day_dir(date, user) / filename
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")
    if not f.exists():
        raise HTTPException(status_code=404, detail=f"{filename} not found")
    return PlainTextResponse(f.read_text(encoding="utf-8"))


@app.get("/api/days/{date}/raw-text", )
def raw_text(date: str, user: UserContext = Depends(require_user)):
    return _day_text_file(date, "raw_text.md", user)


@app.get("/api/days/{date}/sorted-notes", )
def sorted_notes(date: str, user: UserContext = Depends(require_user)):
    return _day_text_file(date, "sorted_notes.md", user)


@app.post("/api/entries/image", )
async def add_image(
    date: str = Form(...),
    note: str = Form(""),
    exclude_from_diary: bool = Form(False),
    image: UploadFile = File(...),
    user: UserContext = Depends(require_user),
):
    try:
        p = day_dir(date, user)
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")
    suffix = (image.filename or "image.jpg").split(".")[-1].lower()
    if suffix not in {"jpg", "jpeg", "png", "webp"}:
        raise HTTPException(status_code=400, detail="bad image type")
    img_id = "img_" + uuid4().hex[:12]
    filename = f"{img_id}.{suffix}"
    img_path = p / "images" / filename
    await _save_upload_limited(image, img_path, settings().max_image_upload_bytes)
    # Vision description is deferred (proxy returns 502 for images). When
    # disabled, or if the vision call fails, still archive the image with a
    # placeholder description and mark it "stored" so the photo is never lost
    # and the day's flow keeps working. Flip VISION_ENABLED=1 once a
    # vision-capable upstream is wired up. See design doc "视觉识图（暂缓）".
    if settings().vision_enabled:
        try:
            desc = await describe_image(img_path, note)
            status = "described"
        except RuntimeError:
            desc = _placeholder_description(filename, note, failed=True)
            status = "stored"
    else:
        desc = _placeholder_description(filename, note, failed=False)
        status = "stored"
    (p / "image_descriptions" / f"{img_id}.md").write_text(desc, encoding="utf-8")
    now = datetime.now(timezone.utc).isoformat()
    with conn(user) as c:
        c.execute(
            "insert into entries(id,date,type,created_at,filename,note,status,exclude_from_diary) values(?,?,?,?,?,?,?,?)",
            (img_id, date, "image", now, filename, note, status, int(exclude_from_diary)),
        )
    audit(date, user, "image.add", img_id)
    return {"id": img_id, "status": status}


def _placeholder_description(filename: str, note: str, *, failed: bool) -> str:
    reason = (
        "视觉识图调用失败（上游暂不可用），图片已归档待人工/后续处理。"
        if failed
        else "视觉识图当前已暂缓（VISION_ENABLED=0），图片已归档待后续处理。"
    )
    return f"""# 图片描述：{filename}

## 基本信息
- 用户补充说明：{note or "（无）"}

## 状态
{reason}

## 与实习任务的可能关系
待补充：可结合当天文字记录人工确认。

## 可写入正式日记的表述
待补充。

## 不建议写入的内容
待人工确认。

## 置信度与待确认点
未调用视觉模型，全部待确认。
"""


def collect_day_source(date: str, user: UserContext) -> str:
    p = day_dir(date, user)
    parts = []
    raw = p / "raw_text.md"
    if raw.exists():
        parts.append(raw.read_text(encoding="utf-8"))
    for f in sorted((p / "image_descriptions").glob("*.md")):
        parts.append(f.read_text(encoding="utf-8"))
    return "\n\n---\n\n".join(parts)


def _parse_day(value: str) -> date_type:
    try:
        parsed = datetime.strptime(value, "%Y-%m-%d").date()
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")
    if parsed.isoformat() != value:
        raise HTTPException(status_code=400, detail="bad date")
    return parsed


def _range_days(start_date: str, end_date: str) -> list[str]:
    start = _parse_day(start_date)
    end = _parse_day(end_date)
    if end < start:
        raise HTTPException(status_code=400, detail="end_date before start_date")
    return [(start + timedelta(days=i)).isoformat() for i in range((end - start).days + 1)]


def collect_range_source(start_date: str, end_date: str, user: UserContext) -> str:
    parts = []
    for d in _range_days(start_date, end_date):
        p = day_path(d, user)
        day_parts = []
        sorted_notes_path = p / "sorted_notes.md"
        raw_path = p / "raw_text.md"
        if sorted_notes_path.exists():
            day_parts.append(sorted_notes_path.read_text(encoding="utf-8"))
        elif raw_path.exists():
            day_parts.append(raw_path.read_text(encoding="utf-8"))
        desc_dir = p / "image_descriptions"
        if desc_dir.exists():
            day_parts.extend(f.read_text(encoding="utf-8") for f in sorted(desc_dir.glob("*.md")))
        if day_parts:
            parts.append(f"## {d}\n\n" + "\n\n---\n\n".join(day_parts))
    return "\n\n======\n\n".join(parts)


@app.get("/api/report-templates", )
def report_templates(user: UserContext = Depends(require_user)):
    return {"templates": load_report_templates()}


@app.post("/api/actions/generate-report", )
async def generate_report(body: ReportGenerateIn, user: UserContext = Depends(require_user)):
    _range_days(body.start_date, body.end_date)
    templates = load_report_templates()
    template = next(
        (
            item for item in templates
            if item["id"] == body.template_id or (not body.template_id and item["type"] == body.type)
        ),
        None,
    )
    if template is None:
        raise HTTPException(status_code=404, detail="template not found")
    if template["type"] != body.type:
        raise HTTPException(status_code=400, detail="template type mismatch")
    template_id = template["id"]

    source = collect_range_source(body.start_date, body.end_date, user)
    validation = [] if source else ["no_source"]
    word_count = body.word_count or _DEFAULT_REPORT_WORDS[body.type]
    try:
        markdown = await generate_report_markdown(
            body.type,
            body.start_date,
            body.end_date,
            source,
            word_count,
            body.extra_instruction,
        )
    except RuntimeError:
        raise HTTPException(status_code=502, detail="generate-report failed")

    report_id = f"{body.type}-{body.start_date}_{body.end_date}-{uuid4().hex[:4]}"
    p = report_dir(report_id, user)
    (p / "report.md").write_text(markdown, encoding="utf-8")
    render_report_docx(
        body.type,
        body.start_date,
        body.end_date,
        "",
        markdown.splitlines(),
        user.profile_values,
        p / "report.docx",
        template_id,
    )
    meta = {
        "status": "drafted",
        "report_id": report_id,
        "type": body.type,
        "start_date": body.start_date,
        "end_date": body.end_date,
        "template_id": template_id,
        "word_count": word_count,
        "markdown_file": "report.md",
        "docx_file": "report.docx",
        "validation": validation,
    }
    (p / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    return {**meta, "markdown": markdown}


def _report_file(report_id: str, name: str, user: UserContext):
    try:
        f = report_dir(report_id, user, create=False) / name
    except ValueError:
        raise HTTPException(status_code=404, detail="report not found")
    if not f.exists():
        raise HTTPException(status_code=404, detail="report not found")
    return f


@app.get("/api/reports/{report_id}", )
def report_meta(report_id: str, user: UserContext = Depends(require_user)):
    return json.loads(_report_file(report_id, "meta.json", user).read_text(encoding="utf-8"))


@app.get("/api/reports/{report_id}/draft", )
def report_draft(report_id: str, user: UserContext = Depends(require_user)):
    f = _report_file(report_id, "report.md", user)
    return FileResponse(f, media_type="text/markdown; charset=utf-8", filename=f"{report_id}.md")


@app.get("/api/reports/{report_id}/files/report.docx", )
def report_docx(report_id: str, user: UserContext = Depends(require_user)):
    f = _report_file(report_id, "report.docx", user)
    return FileResponse(f, filename=f"{report_id}.docx")


@app.post("/api/actions/sort-day", )
async def sort_day(body: SortIn, user: UserContext = Depends(require_user)):
    try:
        text = await sort_day_text(collect_day_source(body.date, user), body.extra_instruction)
    except RuntimeError:
        raise HTTPException(status_code=502, detail="sort-day failed")
    (day_dir(body.date, user) / "sorted_notes.md").write_text(text, encoding="utf-8")
    audit(body.date, user, "day.sort", "ok")
    return {"status": "sorted"}


@app.post("/api/actions/generate-diary", )
async def generate_diary(body: GenerateIn, user: UserContext = Depends(require_user)):
    p = day_dir(body.date, user)
    source = (
        (p / "sorted_notes.md").read_text(encoding="utf-8")
        if (p / "sorted_notes.md").exists()
        else collect_day_source(body.date, user)
    )
    try:
        data = await generate_diary_json(body.date, source, body.word_count, body.extra_instruction)
    except RuntimeError:
        raise HTTPException(status_code=502, detail="generate-diary failed")
    md = "# " + data["title"] + "\n\n" + "\n\n".join(data["body_paragraphs"]) + "\n"
    (p / "diary_draft.md").write_text(md, encoding="utf-8")
    audit(body.date, user, "diary.draft", "ok")
    render_docx(
        body.date,
        data["title"],
        data["body_paragraphs"],
        user.profile_values,
        p / "diary_final.docx",
    )
    audit(body.date, user, "diary.docx", "ok")
    return {"status": "drafted", "draft": md}


@app.get("/api/days/{date}/draft", )
def download_draft(date: str, user: UserContext = Depends(require_user)):
    try:
        f = day_dir(date, user) / "diary_draft.md"
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")
    if not f.exists():
        raise HTTPException(status_code=404, detail="draft not found")
    return PlainTextResponse(f.read_text(encoding="utf-8"))


@app.get("/api/days/{date}/files/diary_final.docx", )
def download_docx(date: str, user: UserContext = Depends(require_user)):
    try:
        f = day_dir(date, user) / "diary_final.docx"
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")
    if not f.exists():
        raise HTTPException(status_code=404, detail="docx not found")
    return FileResponse(f, filename=f"diary_final_{date}.docx")


@app.get("/api/days/{date}/image-descriptions", )
def list_image_descriptions(date: str, user: UserContext = Depends(require_user)):
    try:
        p = day_dir(date, user)
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")
    out = {}
    for f in sorted((p / "image_descriptions").glob("*.md")):
        out[f.stem] = f.read_text(encoding="utf-8")
    return out


@app.post("/api/assistant/chat", )
async def assistant_chat_endpoint(body: AssistantChatIn, user: UserContext = Depends(require_user)):
    messages = [{"role": m.role, "content": m.content} for m in body.messages]
    try:
        reply = await assistant_chat(messages)
    except RuntimeError:
        raise HTTPException(status_code=502, detail="assistant chat failed")
    return {"reply": reply}


@app.post("/api/assistant/diary-edit/preview", )
async def diary_edit_preview(body: DiaryEditPreviewIn, user: UserContext = Depends(require_user)):
    try:
        p = day_dir(body.date, user)
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")

    context_files: dict[str, str] = {}
    for name in ("sorted_notes.md", "diary_draft.md"):
        f = p / name
        if f.exists():
            context_files[name] = f.read_text(encoding="utf-8")
    for f in sorted((p / "image_descriptions").glob("*.md")):
        context_files[f"image_descriptions/{f.name}"] = f.read_text(encoding="utf-8")

    history = [{"role": m.role, "content": m.content} for m in body.messages]
    try:
        result = await assistant_diary_preview(body.date, body.instruction, context_files, history, body.targets)
    except RuntimeError:
        raise HTTPException(status_code=502, detail="diary-edit preview failed")

    changes = []
    for c in result["changes"]:
        target = c["target"]
        if not _is_allowed_edit_target(target) or not _target_requested(target, body.targets):
            continue
        c["before_content"] = context_files.get(target, "")
        changes.append(c)
    preview_id = "prev_" + uuid4().hex[:16]
    _diary_edit_previews[preview_id] = {
        "workspace": user.workspace,
        "date": body.date,
        "changes": changes,
    }
    audit(body.date, user, "diary_edit.preview", preview_id)
    return {"reply": result["reply"], "preview_id": preview_id, "changes": changes}


@app.post("/api/assistant/diary-edit/confirm", )
async def diary_edit_confirm(body: DiaryEditConfirmIn, user: UserContext = Depends(require_user)):
    preview = _diary_edit_previews.get(body.preview_id)
    if preview is None or preview["workspace"] != user.workspace:
        raise HTTPException(status_code=404, detail="preview not found")

    date = preview["date"]
    try:
        p = day_dir(date, user)
    except ValueError:
        raise HTTPException(status_code=400, detail="bad date")

    changed_targets = []
    for change in preview["changes"]:
        target = change["target"]
        if not _is_allowed_edit_target(target):
            raise HTTPException(status_code=400, detail=f"target not allowed: {target}")
        (p / target).parent.mkdir(parents=True, exist_ok=True)
        (p / target).write_text(change["new_content"], encoding="utf-8")
        changed_targets.append(target)

    del _diary_edit_previews[body.preview_id]
    audit(date, user, "diary_edit.confirm", body.preview_id)
    return {"status": "applied", "changed_targets": changed_targets}





