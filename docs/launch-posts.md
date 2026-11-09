# Launch posts — ready to paste

Repo: **https://github.com/xiangbiaoqiao/scrape-studio**

Post as yourself and disclose that you are the author. Do not ask for stars or use vote rings. Read each community's self-promotion rules before submitting; if a subreddit restricts promotion to a weekly thread, use that thread instead.

Suggested order (all times UTC):

| When | Where | Why |
| --- | --- | --- |
| Tue/Wed 14:00–16:00 | Hacker News | US morning, highest chance of front page |
| Same day, +1h | r/webscraping | Practitioners who will actually try it |
| Same day, +3h | V2EX `分享创造` | Chinese audience, evening peak (21:00–23:00 CST) |

Do not post the same link to several subreddits at once — that reads as spam.

---

## 1. Hacker News — Show HN

**URL:** https://news.ycombinator.com/submit

**Title**

```
Show HN: ScrapeStudio – visual scraping workbench with an anti-bot doctor
```

**URL field**

```
https://github.com/xiangbiaoqiao/scrape-studio
```

**Text (optional, paste into the `text` box)**

```
I built ScrapeStudio because most scraping tools stop at "extract these selectors".
The hard part is everything after that: the site changes, starts returning 403/429,
serves a challenge page, or silently returns zero rows.

It is a self-hosted workbench with three parts:
- Vue 3 builder: click elements on a rendered screenshot, get scored CSS/XPath
  candidates, preview real rows.
- Spring Boot API: projects, JWT auth, async runs, scheduling, CSV/JSON export,
  Python/Java/Node code generation.
- Playwright worker: Chromium rendering, extraction, and a doctor that inspects
  TLS, HTTP/2, CDN headers, bot cookies, robots.txt and challenge markers.

The stack is deliberately boring: Java 17, Spring Boot 3, PostgreSQL, Vue 3,
TypeScript, Playwright. Docker Compose included.

It does not solve CAPTCHAs or rotate proxies. The doctor is diagnostic, not an
evasion tool.

I would especially value feedback on the selector scoring and the run/scheduling
model.
```

---

## 2. Reddit — r/webscraping

Check the sidebar first. If self-promotion is limited, post this in the weekly
"show and tell" thread instead of a standalone post.

**Title**

```
I built a self-hosted visual scraping workbench with a built-in anti-bot doctor
```

**Body**

```
Most of my scraping time goes into the same loop: write selectors, run it, get
zero rows, discover the markup changed or the site started serving a challenge.

So I built ScrapeStudio, a self-hosted workbench that covers that whole loop:

- Visual builder: click elements on a rendered screenshot, get scored CSS/XPath
  candidates instead of guessing.
- Live preview: run the selectors against the real page and see up to 500 rows
  before saving.
- Code export: Python (requests + BeautifulSoup, Playwright for XPath), Java
  (Jsoup), Node.js (Playwright).
- Runs: async execution, interval scheduling, history, CSV/JSON export.
- Anti-bot doctor: checks HTTP status, challenge markers, CDN fingerprint, TLS
  version/cipher/ALPN, HTTP/2, bot cookies, redirects and robots.txt, then gives
  a plain-language risk score.

Stack: Spring Boot 3 / Java 17, PostgreSQL, Vue 3 + TypeScript, Playwright.
Docker Compose included, MIT.

What it does not do: solve CAPTCHAs, rotate proxies, or bypass protections. The
doctor only diagnoses.

Repo (screenshots + architecture doc): https://github.com/xiangbiaoqiao/scrape-studio

I'm the author. Happy to answer questions about the selector scoring or the
worker design, and I'd like to know which feature you'd prioritize next —
pagination is the obvious gap.
```

---

## 3. V2EX — 分享创造

**发布地址:** https://www.v2ex.com/new

节点选择 **分享创造**。

**标题**

```
我开源了一个可视化采集工作台：点选页面、预览数据、生成代码，还带反爬诊断
```

**正文**

```
做采集最耗时的往往不是写代码，而是后面这些事：页面改版、403/429、挑战页、
凌晨任务悄悄返回 0 条数据。

所以我写了一个自托管的可视化采集工作台 ScrapeStudio，把整个流程串起来：

· 可视化选择器
  在渲染后的页面截图上直接点元素，自动给出多个 CSS/XPath 候选并打分，不用猜选择器。

· 实时预览
  保存前就能拿真实页面跑一遍，最多返回 500 行结构化数据，空结果会提示原因。

· 代码生成
  同一个任务可以导出 Python（requests + BeautifulSoup，XPath 走 Playwright）、
  Java（Jsoup）、Node.js（Playwright），复制或下载即可。

· 运行与调度
  异步执行、按间隔定时、运行历史、耗时和条数统计、CSV/JSON 导出。

· 反爬诊断（这个是我最想做的部分）
  检查 HTTP 状态、挑战页特征、CDN 指纹、TLS 版本/加密套件/ALPN、HTTP/2、
  bot cookie、重定向和 robots.txt，最后给出一个风险评分和直白的原因说明。

技术栈：Spring Boot 3 + Java 17 + PostgreSQL + Vue 3 + TypeScript + Playwright，
Docker Compose 一键起，MIT。

边界说清楚：不破解验证码、不轮换代理，诊断工具只负责告诉你"哪里不对"，
不承诺绕过任何防护。

仓库（含 9 张界面截图和架构文档）：
https://github.com/xiangbiaoqiao/scrape-studio

我是作者，欢迎提需求和 issue。下一步大概率补分页翻页，也想听听大家实际
卡在哪一步。
```

---

## 4. After posting

- Reply to every technical question within a few hours on launch day.
- Do not argue with criticism; ask for reproduction steps and file an issue.
- If someone reports a real bug, fix it in a small PR and link the PR in the thread.
- Track which channel sent traffic with GitHub's referrer stats (Insights → Traffic).
