# syntax=docker/dockerfile:1.7

FROM node:24-alpine AS frontend
WORKDIR /src
ARG PNPM_VERSION=11.9.0
ARG NPM_CONFIG_REGISTRY=https://registry.npmjs.org
RUN npm install -g pnpm@$PNPM_VERSION --registry=$NPM_CONFIG_REGISTRY && \
    pnpm config set registry $NPM_CONFIG_REGISTRY
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./web/
WORKDIR /src/web
RUN pnpm install --frozen-lockfile
WORKDIR /src
COPY web ./web
COPY server/router/frontend ./server/router/frontend
RUN pnpm --dir web build

FROM golang:1.25-alpine AS backend
WORKDIR /src
ARG GOPROXY=https://proxy.golang.org,direct
ENV GOPROXY=$GOPROXY
RUN apk add --no-cache ca-certificates git
COPY go.mod go.sum ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
COPY proto/gen ./proto/gen
COPY server ./server
COPY store ./store
COPY scripts/entrypoint.sh ./scripts/entrypoint.sh
COPY --from=frontend /src/server/router/frontend/dist ./server/router/frontend/dist
ARG TARGETOS TARGETARCH
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -ldflags="-s -w" -tags netgo,osusergo -o sillage ./cmd/sillage

FROM alpine:3.21 AS runtime
RUN apk add --no-cache ca-certificates tzdata su-exec && \
    addgroup -g 10001 -S sillage && \
    adduser -u 10001 -S -G sillage -h /var/opt/sillage sillage && \
    mkdir -p /var/opt/sillage /usr/local/sillage && \
    chown -R sillage:sillage /var/opt/sillage /usr/local/sillage

COPY --from=backend /src/sillage /usr/local/sillage/sillage
COPY --from=backend /src/scripts/entrypoint.sh /usr/local/sillage/entrypoint.sh
RUN chmod 755 /usr/local/sillage/entrypoint.sh

USER root
WORKDIR /var/opt/sillage
VOLUME /var/opt/sillage

ENV SILLAGE_ADDR="0.0.0.0" \
    SILLAGE_PORT="5231" \
    SILLAGE_DATA="/var/opt/sillage" \
    SILLAGE_LOG_FORMAT="json" \
    SILLAGE_LOG_LEVEL="info" \
    SILLAGE_MAX_UPLOAD_MB="30" \
    TZ="UTC"

EXPOSE 5231

ENTRYPOINT ["/usr/local/sillage/entrypoint.sh", "/usr/local/sillage/sillage"]
