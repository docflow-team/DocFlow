FROM golang:1.27.4-bookworm AS build

# RELEASE.2025-10-15T17-29-55Z; verify the exact upstream source archive.
ARG MINIO_COMMIT=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
ARG MINIO_SOURCE_SHA256=45521908307306e925c98d629e1c17d78c8b72b6ee242b1bfb1409f7d8ee5841

WORKDIR /src
RUN curl --fail --location --retry 3 \
        "https://codeload.github.com/minio/minio/tar.gz/${MINIO_COMMIT}" -o minio-source.tar.gz \
    && echo "${MINIO_SOURCE_SHA256}  minio-source.tar.gz" | sha256sum --check \
    && tar --extract --gzip --file minio-source.tar.gz --strip-components=1
RUN CGO_ENABLED=0 go build -p 2 -trimpath \
    -ldflags="-s -w \
        -X github.com/minio/minio/cmd.Version=2025-10-15T17:29:55Z \
        -X github.com/minio/minio/cmd.CopyrightYear=2025 \
        -X github.com/minio/minio/cmd.ReleaseTag=RELEASE.2025-10-15T17-29-55Z \
        -X github.com/minio/minio/cmd.CommitID=${MINIO_COMMIT}" \
    -o /out/minio .

FROM debian:bookworm-slim

LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 minio \
    && useradd --uid 10001 --gid minio --no-create-home minio \
    && mkdir /data \
    && chown minio:minio /data

COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /src/LICENSE /src/NOTICE /src/CREDITS /src/minio-source.tar.gz /usr/share/doc/minio/

USER minio:minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
