# Launch playbook

A practical checklist for taking ScrapeStudio from a local repository to a credible public open-source project. It assumes the repository is `xiangbiaoqiao/scrape-studio`; adjust the slug if needed.

No step in this document has been executed against GitHub yet. Do not push or publish without the owner's explicit approval.

## 1. What is already done

| Item | Status |
| --- | --- |
| Three services implemented (Spring Boot, Vue 3, Playwright worker) | ✅ |
| Auth, projects, fields, render, pick, preview, runs, exports, scheduling, dashboard, doctor | ✅ |
| Demo workspace seeded (`demo@scrapestudio.dev` / `demo1234`) | ✅ |
| Backend tests (`mvn test`) | ✅ 6 tests |
| Frontend typecheck and production build | ✅ |
| Worker typecheck and build | ✅ |
| API end-to-end suite | ✅ |
| Browser end-to-end flow at `vite preview` | ✅ zero console errors, zero failed requests |
| README screenshots (9, 2× DPR) | ✅ `docs/screenshots/` |
| Dockerfiles, Nginx config, Compose, `.env.example` | ✅ |
| GitHub Actions CI | ✅ |
| README, Chinese README, architecture, launch, resume docs | ✅ |
| Git repository initialized | ❌ |
| Docker Compose actually run locally | ❌ (Docker not installed on the build machine) |
| Public demo deployed | ❌ |

## 2. Pre-flight before publishing

### 2.1 Re-run the checks

```bash
# Backend
cd backend
mvn test
mvn -DskipTests package

# Frontend
cd ../frontend
npm ci
npm run typecheck
npm run build

# Worker
cd ../worker
npm ci
npm run typecheck
npm run build
```

Also run the app once from a clean database:

1. Stop the backend.
2. Delete `backend/data/` (local H2 only — never a production database).
3. Start worker, backend, and frontend.
4. Log in with the demo account.
5. Render, pick, preview, generate all three languages, run, export CSV/JSON, open the doctor.
6. Confirm the dashboard has data and no console errors.

### 2.2 Validate Docker on a machine that has it

Docker was not available during development, so the Compose topology is statically validated only. Before announcing the project:

```bash
cp .env.example .env
# Set strong POSTGRES_PASSWORD, JWT_SECRET, WORKER_TOKEN
docker compose config
docker compose up --build -d
docker compose ps
```

Then:

- Open `http://localhost:8088` and log in.
- Render and preview a page (this exercises the worker and screenshot proxy).
- Restart the stack and confirm projects/runs survive (PostgreSQL volume).
- Check `docker compose logs backend worker web` for warnings.
- Confirm `/screenshots/<uuid>.png` works through Nginx.

### 2.3 Repository hygiene

```bash
git status --ignored
```

Confirm these are **not** tracked:

- `.env` or any file containing real secrets
- `backend/data/` (H2 files)
- `worker/.data/` (screenshots)
- `node_modules/`, `dist/`, `target/`
- `.ui-check*.mjs`, `.status-*.mjs`, `.net-check.mjs` (local QA scripts)
- editor/OS files

`docs/screenshots/*.png` **should** be tracked — they are README assets.

Search for accidental secrets:

```bash
git grep -n -i "password\|secret\|token" -- ':!*.md' ':!*.example' ':!.env.example'
```

Development defaults (`dev-worker-token`, `dev-only-change-me...`) are intentional and documented; real credentials must never appear.

### 2.4 Link and asset check

- README image paths resolve on GitHub (relative paths, correct case).
- Badge URLs use the final repository slug.
- LICENSE year and name are correct.
- `.github/workflows/ci.yml` passes on the first push.
- All screenshots show the final UI (the status pill fix is included).

## 3. Publish the repository

### 3.1 Initialize and commit

```bash
cd scrape-studio
git init
git add .
git commit -m "feat: ScrapeStudio v0.1.0 — visual scraping workbench with anti-bot doctor"
git branch -M main
```

Review the staged file list before committing:

```bash
git status
git diff --cached --stat
```

### 3.2 Create the GitHub repository

**Option A — GitHub CLI**

```bash
# Install: https://cli.github.com/
gh auth login
gh repo create xiangbiaoqiao/scrape-studio \
  --public \
  --description "Visual web scraping workbench with a built-in anti-bot doctor. Spring Boot + Vue 3 + Playwright." \
  --source . \
  --remote origin \
  --push
```

**Option B — Web UI**

1. Create a new public repository named `scrape-studio`.
2. Do **not** initialize it with a README, license, or `.gitignore`.
3. Add the remote and push:

```bash
git remote add origin https://github.com/xiangbiaoqiao/scrape-studio.git
git push -u origin main
```

### 3.3 Repository settings

| Setting | Value |
| --- | --- |
| Description | `Visual web scraping workbench with a built-in anti-bot doctor. Spring Boot + Vue 3 + Playwright.` |
| Website | Public demo URL once deployed (optional) |
| Topics | `web-scraping`, `playwright`, `spring-boot`, `vue3`, `typescript`, `java`, `visual-editor`, `anti-bot`, `selenium-alternative`, `data-extraction`, `self-hosted`, `scraper` |
| Social preview | Upload `docs/screenshots/dashboard.png` (1280×640 crop works best) |
| Features | Enable Issues and Discussions |
| Releases | Create `v0.1.0` after CI is green |

### 3.4 Release notes (v0.1.0)

Suggested body:

```markdown
## ScrapeStudio v0.1.0

The first public release of a self-hosted visual scraping workbench.

### Highlights
- Click-to-select visual builder with scored CSS/XPath candidates
- Live preview of up to 500 rows
- Python, Java, and Node.js code generation
- Async runs, CSV/JSON export, and interval scheduling
- Built-in anti-bot doctor: TLS, HTTP/2, CDN headers, cookies, robots.txt, challenge detection
- Dashboard, JWT auth, demo workspace, Docker Compose deployment

### Stack
Spring Boot 3.3 · Java 17 · Vue 3 · TypeScript · Playwright · PostgreSQL

### Known limitations
- Java code generation targets Jsoup and expects CSS selectors; use Python or Node for XPath projects.
- The scheduler is single-node; multiple API replicas need a distributed lock.
- Screenshot URLs are unguessable but not access-controlled. Run behind SSO for untrusted users.
- No CAPTCHA solving, proxy rotation, or pagination traversal yet.

See README.md for setup and docs/ARCHITECTURE.md for design details.
```

## 4. Optional public demo

A demo dramatically improves conversion, but only if it is reliable.

Recommended shape:

- One small VPS (2 vCPU / 4 GB RAM) or a container platform with a persistent disk.
- Docker Compose from this repo.
- Caddy or Nginx in front for TLS.
- Dedicated demo account, seeded data, and a nightly reset (drop the Postgres volume and restart).
- Rate-limit account creation or disable registration in the demo profile.

Caddy example (fronting the Nginx container on `8088`):

```caddyfile
demo.yourdomain.com {
    reverse_proxy 127.0.0.1:8088
}
```

Set in `.env`:

```text
WEB_PORT=8088
PUBLIC_BASE_URL=https://demo.yourdomain.com
CORS_ORIGINS=https://demo.yourdomain.com
DEMO_ENABLED=true
```

Add a visible banner or README note that the demo is reset periodically and that external targets may be unreachable from the host's network.

## 5. Launch posts

Keep the tone factual: what it does, how it is built, what is hard, what is missing. Do not ask for stars, do not use fake accounts, and disclose that you are the author.

### Show HN

**Title:** Show HN: ScrapeStudio — visual scraping workbench with an anti-bot doctor

**Body:**

> I built ScrapeStudio because most scraping tools stop at selector extraction. The hard part is usually everything after that: the site changes, starts returning 403/429, serves a challenge page, or silently returns zero rows.
>
> It is a self-hosted workbench with three parts:
> - Vue 3 builder: click elements on a rendered screenshot, get scored CSS/XPath candidates, preview real rows.
> - Spring Boot API: projects, JWT auth, async runs, scheduling, CSV/JSON export, Python/Java/Node code generation.
> - Playwright worker: Chromium rendering, extraction, and a doctor that inspects TLS, HTTP/2, CDN headers, bot cookies, robots.txt, and challenge markers.
>
> The stack is deliberately boring: Java 17, Spring Boot 3, PostgreSQL, Vue 3, TypeScript, Playwright. Docker Compose included.
>
> It does not solve CAPTCHAs or rotate proxies. The doctor is diagnostic, not an evasion tool.
>
> Repo: https://github.com/xiangbiaoqiao/scrape-studio
>
> I would especially value feedback on the selector scoring and the run/scheduling model.

### Reddit — r/webscraping

**Title:** I built a self-hosted visual scraping workbench with a built-in anti-bot doctor

Use the same body, shortened, and check the subreddit's self-promotion rules before posting. Lead with the problem and the architecture, not the link.

### X / Twitter thread

1. Most scraping tools stop at “extract these selectors”. The real work starts when the site fights back.
2. I built ScrapeStudio: click elements on a screenshot, preview rows, generate Python/Java/Node code, schedule runs.
3. It also ships an anti-bot doctor: TLS 1.3? HTTP/2? Cloudflare? challenge page? robots.txt? One report, plain language.
4. Stack: Spring Boot 3 + Vue 3 + TypeScript + Playwright + PostgreSQL. Docker Compose, MIT.
5. Repo + screenshots: https://github.com/xiangbiaoqiao/scrape-studio — feedback welcome.

Attach `dashboard.png` and `builder.png`.

### LinkedIn

Use a short problem → solution → stack → link format. Mention the three-service architecture and the diagnosis feature. Avoid buzzwords; attach the builder screenshot.

### Chinese communities (V2EX / 掘金 / 少数派)

**标题：** 我开源了一个可视化采集工作台：点选页面、预览数据、生成代码，还带反爬诊断

正文结构：

1. 痛点：选择器难调、页面改版、403/429、凌晨空跑。
2. 功能：截图点选、字段管理、实时预览、三语言代码生成、定时运行、CSV/JSON、反爬医生。
3. 技术栈：Spring Boot 3 + Java 17 + Vue 3 + TypeScript + Playwright + PostgreSQL。
4. 边界：不破解验证码、不轮换代理；反爬医生只做诊断。
5. 仓库地址与本地启动方式。

附 `builder.png`、`doctor.png`。遵守社区推广规则，先参与讨论再发项目。

## 6. Launch-day checklist

- [ ] CI is green on `main`.
- [ ] Repository is public and the README renders correctly.
- [ ] Social preview image is set.
- [ ] Topics are added.
- [ ] `v0.1.0` release is published.
- [ ] Demo (if any) is reachable and the demo login works.
- [ ] A first issue is pinned: “Feedback: what should v0.2 prioritize?”
- [ ] Response plan: monitor issues for the first 48 hours; acknowledge bugs within a day.
- [ ] Do not argue with negative feedback; ask for reproduction steps and log it.

## 7. First-week follow-up

1. Fix launch-day bugs in small, well-described PRs.
2. Add one high-value request from the feedback (pagination is the most likely).
3. Publish a short “what I learned building the anti-bot doctor” post with real signal examples.
4. Add a `good first issue` label and 2–3 scoped issues.
5. Update the README with any new screenshots or deployment notes.

## 8. Honest risks

- **Demo-site availability.** `books.toscrape.com` and `quotes.toscrape.com` were intermittently unreachable from the build network. The screenshots use saved run data and route fixtures, which is fine for documentation, but a public demo should be tested from its own host.
- **Docker not locally executed.** Compose and Dockerfiles are statically reviewed; run them once before launch.
- **Anti-bot claims.** Keep the language precise. The doctor detects signals; it does not guarantee access or bypass protection.
- **Security expectations.** A scraping tool that fetches arbitrary URLs is inherently sensitive. Document SSRF and screenshot exposure clearly, and default to self-hosted single-tenant use.

## 9. Success metrics to watch

- Stars and forks (vanity, but useful for reach).
- Issues that describe real workflows, not drive-by requests.
- Clone-to-first-run success: can a new user get the demo login working in under 10 minutes?
- Codegen and doctor usage relative to render/preview (instrument later if a demo is deployed).

The goal of the launch is not a spike. It is a first group of users who trust the project enough to file a useful issue.
