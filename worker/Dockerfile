FROM mcr.microsoft.com/playwright:v1.65.0-noble

WORKDIR /app

ENV NODE_ENV=production \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    SCREENSHOT_DIR=/app/.data/screenshots

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY tsconfig.json ./
COPY src ./src
RUN npm run build \
    && mkdir -p /app/.data/screenshots \
    && chown -R pwuser:pwuser /app

USER pwuser
EXPOSE 8787

CMD ["node", "dist/index.js"]
