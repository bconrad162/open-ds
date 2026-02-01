# OpenDS Bridge (Local Helper)

This helper runs locally and exposes a WebSocket bridge for the Chrome extension UI.

## Build

```bash
cd "Chrome extension/helper"
go mod tidy
go build -o opends-bridge ./cmd/opends-bridge
```

## Run

```bash
./opends-bridge
```

The bridge listens on `ws://127.0.0.1:5805`.

## Next steps

Wire the handler to:
- DS UDP/TCP sockets (control + telemetry)
- USB joystick input (native OS APIs)
- NetworkTables client (optional)
