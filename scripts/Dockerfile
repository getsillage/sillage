# syntax=docker/dockerfile:1.7

FROM node:24-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS frontend
WORKDIR /src
ARG PNPM_VERSION=11.9.0
ARG NPM_CONFIG_REGISTRY=https://registry.npmjs.org
ARG VERSION=dev
ARG REVISION=unknown
ENV VITE_SILLAGE_VERSION=$VERSION \
    VITE_SILLAGE_REVISION=$REVISION
RUN npm install -g pnpm@$PNPM_VERSION --registry=$NPM_CONFIG_REGISTRY && \
    pnpm config set registry $NPM_CONFIG_REGISTRY
COPY apps/web/package.json apps/web/pnpm-lock.yaml apps/web/pnpm-workspace.yaml ./apps/web/
WORKDIR /src/apps/web
RUN pnpm install --frozen-lockfile
WORKDIR /src
COPY apps/web ./apps/web
COPY server/router/frontend ./server/router/frontend
RUN pnpm --dir apps/web build

FROM golang:1.26.6-alpine@sha256:3889b425f035be855a72fb4755265311293b6d414521f0a519d819df32222d83 AS backend
WORKDIR /src
ARG GOPROXY=https://proxy.golang.org,direct
ARG VERSION=dev
ARG REVISION=unknown
ENV GOPROXY=$GOPROXY
RUN apk add --no-cache ca-certificates git
COPY go.mod go.sum ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
COPY contracts/proto/gen ./contracts/proto/gen
COPY server ./server
COPY store ./store
COPY scripts/entrypoint.sh ./scripts/entrypoint.sh
COPY --from=frontend /src/server/router/frontend/dist ./server/router/frontend/dist
ARG TARGETOS TARGETARCH
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH \
    go build -trimpath -ldflags="-s -w -X main.version=$VERSION -X main.revision=$REVISION" -tags netgo,osusergo -o sillage ./cmd/sillage

FROM alpine:3.21@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d AS runtime
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="Sillage" \
      org.opencontainers.image.description="Self-hosted, single-user space for private records, history review, and AI answers grounded in your own notes." \
      org.opencontainers.image.source="https://github.com/getsillage/sillage" \
      org.opencontainers.image.url="https://github.com/getsillage/sillage" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version="$VERSION" \
      org.opencontainers.image.revision="$REVISION"
RUN apk add --no-cache ca-certificates tzdata su-exec && \
    addgroup -g 10001 -S sillage && \
    adduser -u 10001 -S -G sillage -h /var/opt/sillage sillage && \
    mkdir -p /var/opt/sillage /usr/local/sillage && \
    chown -R sillage:sillage /var/opt/sillage /usr/local/sillage

COPY --from=backend /src/sillage /usr/local/sillage/sillage
COPY --from=backend /src/scripts/entrypoint.sh /usr/local/sillage/entrypoint.sh
COPY LICENSE NOTICE THIRD_PARTY_NOTICES.md /usr/share/licenses/sillage/
COPY third_party/licenses /usr/share/licenses/sillage/third-party
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
