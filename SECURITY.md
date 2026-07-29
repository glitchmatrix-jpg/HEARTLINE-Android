# Security policy

Report security issues privately rather than opening a public issue.

## Design decisions

- No embedded secrets or API keys.
- No cleartext network traffic.
- No exported app services.
- Notification listener is protected by Android's binding permission.
- PendingIntents are immutable.
- Remote text is treated as untrusted display data and never interpreted as HTML.
- Queries are URL-encoded and length-bounded.
- Network calls have strict connection/read timeouts and do not follow redirects.
- Database and listening history are excluded from cloud backup.
- Release builds enable code and resource shrinking.
