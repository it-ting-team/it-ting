# 프론트엔드(Next.js) 실행 이미지. standalone으로 빌드해 node server.js로 띄운다.
#
# 빌드(저장소 루트에서). 빌드 값은 deploy/<환경>/frontend.build.env에서 읽는다:
#   docker build --build-context buildenv=deploy/test -t it-ting-frontend frontend
# /api/v1/*은 앞단 프록시(deploy/edge의 Caddy)가 백엔드로 바로 넘긴다. 이 이미지는 화면만 준다.
FROM node:24.18.0-slim AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24.18.0-slim AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* 같은 빌드 값. Next가 빌드할 때 .env.production.local을 읽어 JS에 박는다. 비밀값을 넣지 않는다.
COPY --from=buildenv frontend.build.env .env.production.local
ENV NEXT_OUTPUT=standalone NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24.18.0-slim
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
# root가 아닌 사용자로 실행한다.
USER node
EXPOSE 3000
CMD ["node", "server.js"]
