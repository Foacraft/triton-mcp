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

## MCP Testing (Python)

The MCP server uses HTTP+SSE with chunked transfer encoding. Use this script to test all tools:

```python
import socket, json, threading, queue, time

HOST = 'localhost'
PORT = 25580
q = queue.Queue()

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect((HOST, PORT))
sock.settimeout(60)
sock.sendall(b'GET /sse HTTP/1.1\r\nHost: localhost:25580\r\nAccept: text/event-stream\r\nConnection: keep-alive\r\n\r\n')

def recv_line(s):
    buf = b''
    while True:
        c = s.recv(1)
        if not c:
            return buf
        buf += c
        if buf.endswith(b'\r\n'):
            return buf[:-2]

def read_chunk(s):
    size_str = recv_line(s).strip().decode()
    if not size_str:
        return b''
    size = int(size_str, 16)
    if size == 0:
        return b''
    data = b''
    while len(data) < size:
        data += s.recv(size - len(data))
    recv_line(s)
    return data

# Skip HTTP headers
while True:
    line = recv_line(sock)
    if not line:
        break

# Parse session endpoint
chunk = read_chunk(sock)
msg_path = None
for line in chunk.decode().split('\n'):
    if line.startswith('data:') and 'sessionId' in line:
        msg_path = line[5:].strip()

def sse_reader():
    while True:
        try:
            chunk = read_chunk(sock)
            if not chunk or chunk.startswith(b':'):
                continue
            for line in chunk.decode().split('\n'):
                if line.startswith('data:'):
                    data = line[5:].strip()
                    if data.startswith('{'):
                        q.put(json.loads(data))
        except:
            break

t = threading.Thread(target=sse_reader, daemon=True)
t.start()
time.sleep(0.3)

def rpc(method, params, rid):
    body = json.dumps({'jsonrpc': '2.0', 'id': rid, 'method': method, 'params': params})
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect((HOST, PORT))
    s.settimeout(10)
    req = f'POST {msg_path} HTTP/1.1\r\nHost: localhost:{PORT}\r\nContent-Type: application/json\r\nContent-Length: {len(body)}\r\nConnection: close\r\n\r\n{body}'
    s.sendall(req.encode())
    while True:
        try:
            if not s.recv(4096): break
        except: break
    s.close()
    return q.get(timeout=8)

# Initialize
rpc('initialize', {'protocolVersion': '2024-11-05', 'capabilities': {}, 'clientInfo': {'name': 'test', 'version': '1'}}, 0)
print("Initialized OK")

# Call a tool
r = rpc('tools/call', {'name': 'list_languages', 'arguments': {}}, 1)
content = r.get('result', {}).get('content', [{}])[0].get('text', '')
is_err = r.get('result', {}).get('isError', False)
print(f"{'ERR' if is_err else 'OK'}: {content[:200]}")
```

### Key Notes
- SSE response uses `Transfer-Encoding: chunked` — must decode chunks manually (byte-by-byte `recv_line` + `read_chunk`)
- MCP responses arrive on the SSE stream, **not** on the POST response (POST returns `202 Accepted`)
- Must call `initialize` before any `tools/call`
- Wait `0.3s` after starting SSE reader thread before sending first request
