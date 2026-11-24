# ValancedX 1.0.11 — Plugin download transport

- Plugin ZIPs, repository indexes, legacy imports, theme JSON and font downloads use Android platform HTTPS rather than the embedded CIO selector.
- Bounded connection retries on transient DNS/socket/server failures, readable error messages with the failing host.
- Normal system DNS, VPN, proxy, TLS certificate and hostname validation remain in place. HTTPS-only redirects; no hardcoded IPs.
- Response sizes are bounded while reading, and the existing SHA-256, size, manifest and staged ZIP verification remains mandatory.
- Preserves the 1.0.10 manager UI and scroll behavior. Same package and release signing key.
