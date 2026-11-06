# Resume and interview notes

Use this document to describe ScrapeStudio accurately on a resume, in a portfolio, or in an interview. Every number here is measured from the repository; no user or revenue metrics are invented.

## One-line positioning

**ScrapeStudio** — a self-hosted visual web scraping workbench that turns a rendered page into selectors, preview data, generated code, scheduled runs, and an anti-bot diagnosis report. Built with Spring Boot 3, Vue 3, TypeScript, and Playwright.

## Chinese resume version

### 项目名称

ScrapeStudio · 可视化网页采集工作台（个人全栈项目）

### 项目简介

面向数据采集场景的一体化工作台：用户在页面截图上点选元素，系统自动生成并评分 CSS/XPath 选择器；支持字段管理、真实数据预览、Python/Java/Node.js 代码生成、异步运行、定时调度、CSV/JSON 导出，以及基于 TLS、HTTP/2、CDN 响应头、Cookie 和 challenge 特征的反馈诊断。采用 Spring Boot 3 + Vue 3 + Playwright 三服务架构，支持 Docker Compose 一键部署。

### 技术栈

Java 17、Spring Boot 3.3、Spring Security、Spring Data JPA、Flyway、JWT、PostgreSQL/H2、Vue 3、TypeScript、Vite、Pinia、Element Plus、ECharts、Node.js、Fastify、Playwright、Docker、Nginx、GitHub Actions

### 核心工作

- 设计并实现 **前端、后端、渲染 Worker 三服务架构**：后端负责认证、项目/字段、运行编排、调度与代码生成；Worker 隔离 Chromium 渲染、选择器解析、数据抽取和反爬诊断，避免浏览器进程影响 API 稳定性。
- 实现 **截图点选式可视化搭建器**：将点击坐标映射到页面元素，综合稳定 class、`data-*`、祖先路径、`nth-of-type` 与 XPath 生成候选选择器，并按匹配数量与语义质量排序，返回匹配数和文本样例供用户确认。
- 设计 **异步运行模型**：运行记录先落库，事务提交后再投递线程池，避免执行器读取未提交数据；Playwright 调用不占用数据库事务；统一处理成功、失败、被拦截三种状态，并持久化结果、耗时、条数与错误信息。
- 实现 **定时调度与幂等保护**：30 秒扫描到期项目，检测最近一次运行是否仍在执行，必要时顺延 `next_run_at`，避免任务堆叠；支持每项目独立间隔与下次执行时间展示。
- 实现 **三语言代码生成**：根据项目选择器类型自动选择 requests + BeautifulSoup 或 Playwright（Python）、Jsoup（Java）、Playwright（Node.js），支持文本、HTML、任意属性、单值/多值字段，并对引号、反斜杠和空字段做边界处理。
- 实现 **反爬诊断服务**：独立检查 HTTP 状态、challenge 页面特征、CDN 指纹、TLS 1.3/加密套件/ALPN、HTTP/2、反爬 Cookie、重定向链与 robots.txt，输出 0–100 风险分、四级结论和可执行建议。
- 完成 **工程化与交付**：Flyway 管理 4 张表；JWT + BCrypt + 按 owner 过滤的仓储查询实现多用户隔离；Docker Compose 编排 PostgreSQL、后端、Worker、Nginx；GitHub Actions 覆盖三端构建与类型检查；编写架构、部署、发布文档与 9 张 README 截图。
- 建立 **端到端验证**：后端 6 个测试通过；前端 `vue-tsc` 与生产构建通过；Worker 类型检查与构建通过；覆盖登录、项目、渲染、点选、预览、运行、导出、诊断的 API 与浏览器 E2E 流程，浏览器控制台零错误、请求零失败。

### 项目亮点（可量化）

- 3 个可独立部署的服务，20+ REST 路由，4 张业务表
- 7 个前端页面，14 个 Vue 组件/视图，约 5,700 行 Vue 代码
- 3 种代码生成目标，3 种字段取值方式，CSS + XPath 双选择器体系
- 9 张 README 截图，1 套 Docker Compose 生产拓扑，3 个 CI Job

### 可以展开讲的难点

1. **为什么把 Playwright 拆成独立 Worker**：浏览器进程内存占用高、可能崩溃、启动慢；拆开后 API 可以独立重启和水平扩展，Worker 可按需扩容，且渲染超时不会拖垮请求线程。
2. **事务提交后再投递任务**：如果先投递后提交，执行线程可能查不到运行记录；使用 `TransactionSynchronization.afterCommit` 解决竞态。
3. **选择器不是越短越好**：短选择器易读但脆弱，长选择器稳定但可读性差；通过候选评分和匹配数量平衡，并保留 XPath 兜底。
4. **反爬诊断和采集分离**：诊断用 Node `fetch` + `tls` 观察网络层，预览用 Chromium 观察浏览器层；两者目的不同，不能混为一谈。
5. **截图坐标与页面坐标的映射**：Worker 截取固定视口，点击坐标直接对应页面坐标；会话有 TTL，过期后要求重新渲染，避免浏览器上下文泄漏。

## English resume version

### ScrapeStudio — Visual web scraping workbench (personal full-stack project)

**Summary:** A self-hosted workbench that converts a rendered page into scored selectors, live preview rows, generated Python/Java/Node.js code, scheduled runs, CSV/JSON exports, and an anti-bot diagnosis report. Built as three services: Spring Boot API, Vue 3 SPA, and a Playwright rendering worker.

**Stack:** Java 17, Spring Boot 3.3, Spring Security, Spring Data JPA, Flyway, JWT, PostgreSQL/H2, Vue 3, TypeScript, Vite, Pinia, Element Plus, ECharts, Node.js, Fastify, Playwright, Docker, Nginx, GitHub Actions.

**Selected bullets:**

- Architected a **three-service system** (Vue SPA, Spring Boot API, Fastify/Playwright worker) that isolates Chromium rendering and extraction from request handling, so browser crashes and slow pages cannot take down the API.
- Built a **click-to-select visual builder** that maps screenshot coordinates to DOM elements and ranks CSS/XPath candidates using stable classes, data attributes, ancestry, `nth-of-type`, match counts, and text samples.
- Designed an **asynchronous run pipeline** that persists a run before dispatch, submits work after transaction commit, executes Playwright outside any database transaction, and records success, failure, block reason, duration, and item counts.
- Implemented **interval scheduling** with a 30-second dispatcher, overlap detection, and `next_run_at` advancement to prevent duplicate executions.
- Added **three-language code generation** (Python requests/BeautifulSoup or Playwright for XPath, Java Jsoup, Node Playwright) with support for text, HTML, attributes, and multi-value fields.
- Implemented an **anti-bot doctor** covering HTTP status, challenge markers, CDN fingerprints, TLS protocol/cipher/ALPN, HTTP/2, bot cookies, redirects, and robots.txt, producing a 0–100 risk score and actionable recommendations.
- Hardened the app with **JWT + BCrypt authentication**, per-owner repository queries, Flyway migrations, CORS allowlists, worker token authentication, and Docker Compose/Nginx deployment.
- Verified the product with **API and browser end-to-end suites** (login → render → pick → preview → run → export → diagnose) with zero console errors, plus backend tests, frontend typecheck/build, and worker build in CI.

**Scope:** 3 deployable services · 20+ REST routes · 4 tables · 7 UI pages · 3 code-generation targets · 9 documentation screenshots.

## STAR stories for interviews

### 1. A contract bug that only appeared in the UI

**Situation:** The visual builder displayed “undefined matches on page” after picking an element, even though the worker returned the correct count.

**Task:** Find and fix the mismatch without changing the worker API.

**Action:** Compared the worker response, the backend DTO, and the frontend type. The worker returned `itemMatchCount`, but the backend `PickResponse` record omitted the field, so Jackson silently dropped it. Added the field to the record, repackaged the backend, and re-ran the pick flow through the API and UI.

**Result:** The builder now shows the real match count; the API E2E test asserts `itemMatchCount=1`.

**Lesson:** Typed DTOs on both sides are not enough if the contract test stops at HTTP 200. Assert payload fields, not just status codes.

### 2. Runs that could see their own uncommitted row

**Situation:** Early run execution was dispatched inside the same transaction that created the run. Under load, the executor could start before the row was visible.

**Task:** Guarantee the run exists and is committed before Playwright starts.

**Action:** Registered a `TransactionSynchronization.afterCommit` callback to submit the run ID to the executor. Moved the worker call outside the transaction and saved status in a `finally` block.

**Result:** No more “run disappeared” races; slow pages do not hold database connections.

**Lesson:** Async boundaries and transaction boundaries must be designed together.

### 3. Screenshot QA that looked like a CSS bug

**Situation:** The “Success” pill on project cards looked faded in README screenshots.

**Task:** Determine whether the CSS was broken or the capture was misleading.

**Action:** Inspected computed styles and took locator-level screenshots. The tag rendered green and opaque; the full-page capture caught it mid-transition and downscaling made it look washed out. Added a longer settle before capture and re-ran the screenshot suite.

**Result:** Clean screenshots; no incorrect CSS “fix” shipped.

**Lesson:** Measure the rendered artifact before changing styles based on a compressed preview.

### 4. Dev server flakiness versus application failure

**Situation:** The app showed a blank page under the in-app browser; Vite returned sporadic 502s for random modules while `curl` returned 200.

**Task:** Decide whether the product or the dev environment was broken.

**Action:** Verified the production build with `vite preview`, which rendered correctly. Switched UI verification to the production preview, where the full flow passed with zero console errors.

**Result:** Avoided chasing a phantom app bug and validated the release artifact instead.

**Lesson:** Match the test environment to the failure mode; a dev server is not the product.

## Likely interview questions

**Why separate the worker?**
Chromium is heavy, stateful, and failure-prone. Isolating it keeps the API responsive, allows independent scaling, and makes it possible to replace the rendering engine without touching business logic. The worker is stateless between requests except for short-lived render sessions.

**How would you scale it?**
Run multiple stateless worker replicas behind a queue or load balancer. Move render sessions to a shared store (or make picking stateless by re-rendering), add a distributed lock for the scheduler, and move screenshots to object storage. The current in-memory sessions and single-node scheduler are deliberate v0.1 simplifications.

**How do you prevent SSRF?**
Today the app assumes a trusted, self-hosted operator. For untrusted users, run the worker with egress allowlists, block private/link-local CIDRs, require SSO, and serve screenshots through authenticated URLs. This is documented in `docs/ARCHITECTURE.md`.

**Why store rows as JSON instead of a table per project?**
Projects have arbitrary field schemas. JSON keeps the schema flexible and exports trivial; the trade-off is weaker SQL queryability. Runs are capped at 200 rows, so payload size stays bounded.

**How does the selector scoring work?**
The picker synthesizes candidates from stable classes, data attributes, ancestry, `nth-of-type`, and XPath, then ranks them by match count and specificity. The UI shows the count and a text sample so the user can confirm before saving. It is a heuristic, not a guarantee; the preview is the source of truth.

**What happens when a site blocks you?**
`detectBlocked` checks challenge titles, Cloudflare markers, CAPTCHA widgets, 403/429/503, and access-denied pages. Preview returns a blocked flag and reason; runs persist `BLOCKED` with the reason. The doctor explains the network/TLS/CDN signals. The product does not attempt to bypass the block.

**How do you test browser automation?**
Three layers: unit tests for code generation, API E2E for the backend/worker contract, and Playwright browser E2E for the UI. External demo sites are treated as unreliable; screenshot capture uses saved run data and route fixtures when the network is unavailable.

**What would you do differently?**
Add pagination and detail-page traversal first (the most common real workflow), make render sessions stateless, add a distributed scheduler lock, and introduce structured field-level storage for analytics. Also add contract tests that validate JSON fields, not just status codes.

## Project card blurb

> **ScrapeStudio** — Self-hosted visual scraping workbench. Click a page, preview real rows, generate Python/Java/Node code, schedule runs, and diagnose anti-bot blockers. Spring Boot 3 · Vue 3 · TypeScript · Playwright · Docker.

## What not to claim

- Do not claim a user count, star count, or revenue.
- Do not claim the Docker images were executed locally; they were statically reviewed and should be verified on a Docker host.
- Do not claim the tool bypasses CAPTCHAs or anti-bot systems; it diagnoses them.
- Do not claim the demo sites are always reachable; external sandboxes can be blocked by a given network.
