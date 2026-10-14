# Veyra TV: DNS-Based Privacy & Tracker Blocking

## 1. Role in Architecture

DNS-based filtering serves as Veyra's lightweight privacy foundation. By preventing DNS resolution of known advertising and tracking domains, network connections are dropped before TLS handshakes or HTTP requests can occur, saving CPU, bandwidth, and battery/power.

---

## 2. Protection Modes

### Mode A: System Private DNS Guidance (Preferred Native Approach)
- Android 9 (Pie) and higher natively support DNS-over-TLS (DoT) via **Private DNS** (`Settings > Network & internet > Private DNS`).
- Resolvers such as `dns.adguard-dns.com` or custom NextDNS profiles can be configured at the OS level.
- **Advantages**: Protects all device traffic with zero app memory overhead, zero native code, and zero VPN/proxy overhead.
- **Trade-off**: Requires user setup in TV settings. Veyra provides an in-app setup guide.

### Mode B: In-App DNS Protection (Experimental Roadmap)
- Investigates configuring custom DNS endpoints within application networking.
- **Critical Architectural Limitation**: Android System WebView internally issues DNS resolution through its Chromium networking stack (`cronet` / native sockets). Configuring an in-app OkHttp or Java DoH client does **not** automatically intercept WebView's internal socket lookups unless a local proxy or VPN service is introduced.
- In accordance with Section 7.2 of the Master Specification, Veyra will **not** introduce a permanent VPN or background proxy service without explicit approval and demonstrated justification.
