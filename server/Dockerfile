# Use a Debian image instead of Alpine for glibc compatibility with the host (Arch Linux)
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y libssl3 ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app

# Copy the pre-compiled binary from the host machine
COPY target/release/openptt-server .
COPY config ./config
COPY migrations ./migrations

EXPOSE 8443
EXPOSE 9000/udp

ENV RUST_LOG=openptt_server=info,tower_http=info

CMD ["./openptt-server"]
