# triton-mcp Development Notes

## Build & Deploy

```bash
./gradlew clean shadowJar
cp build/libs/triton-mcp-1.0.0.jar /Users/scorez/Desktop/testveloc/plugins/triton-mcp-1.0.0.jar
```

Always use `clean shadowJar` (not just `shadowJar`) when changing version to avoid cached `processResources`.

## Test Server

Located at `/Users/scorez/Desktop/testveloc/`. Start with:

```bash
cd /Users/scorez/Desktop/testveloc
nohup java -jar velocity-3.4.0-SNAPSHOT-541.jar > /tmp/velocity.log 2>&1 &
```

Check logs: `cat /tmp/velocity.log | grep -i "triton\|mcp\|error"`

Kill all instances before restarting: `pkill -f "velocity-3.4.0"`

## MCP Testing (curl)

The MCP server uses **Streamable HTTP** transport (POST to `/mcp`). Test with curl:

```bash
# Step 1: Initialize — captures the mcp-session-id
SESSION=$(curl -s -D - http://localhost:25580/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}' \
  | grep -i "mcp-session-id" | awk '{print $2}' | tr -d '\r')

# Step 2: Send initialized notification
curl -s -o /dev/null http://localhost:25580/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "mcp-session-id: $SESSION" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}'

# Step 3: Call tools (use mcp-session-id header for all subsequent calls)
curl -s http://localhost:25580/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "mcp-session-id: $SESSION" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_languages","arguments":{}}}'
```

### Key Notes
- Transport: **Streamable HTTP** — POST to `/mcp`, responses are direct JSON (not SSE stream)
- Must send `Accept: application/json, text/event-stream` (both required by spec)
- First POST (initialize) returns `mcp-session-id` header — include in all subsequent requests
- `notifications/initialized` returns `202 Accepted` with empty body
- Tool calls return `200 OK` with JSON-RPC response body
