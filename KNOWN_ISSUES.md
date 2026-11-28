# PocketBoot Known Issues & Limitations

- **Microsoft Direct ISO Geo/IP Rate Limits:** Microsoft's software-download portal occasionally rate-limits VPN or datacenter IPs; tap **Retry Microsoft CDN**, switch networks, or use the **NTriver Archive** tab.
- **NTriver ISP DNS / Cloudflare Filtering:** Some mobile ISPs block `ntriver.org` at the DNS layer. PocketBoot automatically falls back to Headless WebView and jsDelivr/GitHub raw mirrors for catalog browsing, and displays exact tier telemetry if direct link generation requires Android Private DNS (`one.one.one.one` or `dns.google`) or a network switch.
- **Android 14/15 OEM MediaProvider Restrictions:** Some OEM ROMs cache MTP file listings aggressively after creating large `.iso`/`.img` files; re-plugging USB or using `adb pull` refreshes visibility if needed.
- **Physical USB & OTG Hardware Coverage:** Automated JVM unit tests (`10` suites) validate pure logic, safety predicates, and parsers only; real-device rooted ConfigFS/UDC and physical USB-C OTG flashing remain hardware-dependent and require physical test verification per device/kernel.
- **CI Native Binary Supply-Chain Pinning:** `.github/workflows/build-apk.yml` now verifies all `10` static ARM64 binaries and boot assets are present and executable, but upstream download checksum verification and immutable Ventoy tag pinning remain tracked in `TODO.md`.
