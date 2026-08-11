# QingGan Production Deployment

Production target for the native iOS app:

- Server: `101.35.247.243`
- OS: Ubuntu 24.04 LTS
- API hostname: `qinggan.findagent.tech`
- Public API base URL used by Release iOS builds: `https://qinggan.findagent.tech`

## Runtime layout

```text
iPhone
  -> HTTPS :443
  -> host Nginx
  -> 127.0.0.1:8080
  -> Spring Boot container
  -> private Docker network
  -> MySQL container
```

MySQL is not published to the public network. Spring Boot is bound only to `127.0.0.1:8080`; Nginx is the public entry point.

## Bootstrap

On the server, install Docker Engine / Docker Compose plugin and Nginx, then clone this repository. From `deploy/production`:

```bash
cp .env.example .env
chmod 600 .env
# Replace both example passwords with different long random values.
docker compose up -d --build
```

Verify the backend locally on the server:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:8080/api/v1/trips/qinggan-2026-family/itinerary
```

## Nginx

Copy `nginx/qinggan.findagent.tech.conf` into the host Nginx sites directory, enable it, validate the Nginx configuration, then reload Nginx.

Before TLS is issued, HTTP can be used only for server/bootstrap verification. The iOS Release build intentionally requires HTTPS and will not accept a plain HTTP production URL.

After domain real-name verification, DNS and ICP prerequisites are ready, issue a TLS certificate for `qinggan.findagent.tech` and configure Nginx HTTPS. Do not weaken iOS App Transport Security to bypass TLS.

## Firewall / cloud security group

Public inbound ports should be limited to:

- `22/tcp` for SSH, restricted by source IP where practical;
- `80/tcp` for HTTP / certificate bootstrap and redirect;
- `443/tcp` for the production API.

Do not expose MySQL `3306/tcp` or Spring Boot `8080/tcp` publicly.

## Resource envelope

The production Compose file is tuned for the current 2-core / 2-GB server:

- Spring Boot JVM heap: max 512 MB;
- backend container limit: 768 MB;
- MySQL buffer pool: 256 MB;
- MySQL container limit: 768 MB.

Revisit these limits only after measuring actual travel-time load.

## Secrets

`deploy/production/.env` is ignored by Git. Never commit production database passwords, device tokens, TLS private keys, or other secrets.
