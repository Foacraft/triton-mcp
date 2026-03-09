# triton-mcp

A Velocity plugin that runs an [MCP](https://modelcontextprotocol.io) server alongside [Triton](https://github.com/tritonmc/Triton), letting AI clients (Claude Desktop, Cursor, etc.) read and write translations directly.

Requires Triton v4 on Velocity with either local or MySQL storage.

## What it does

Exposes Triton's translation data over HTTP+SSE as MCP tools. An AI assistant can list languages, search for missing translations, batch-create entries, and trigger a reload — all without touching config files or the TWIN web UI.

When using MySQL storage, calling `reload_triton` after writes will push a refresh signal to all connected Paper backend servers, same as a normal Triton reload.

## Tools

| Tool | Description |
|---|---|
| `list_languages` | List all configured languages |
| `list_collections` | List collections with item counts and server filters |
| `get_collection_items` | Get all items in a collection |
| `get_item` | Look up a single translation by key |
| `search_items` | Filter by key pattern, content, or missing language |
| `create_text_item` | Create a new translation entry |
| `update_item_translations` | Merge new translations into an existing entry |
| `delete_item` | Delete an entry by key |
| `batch_upsert_items` | Create or update multiple entries in one write |
| `reload_triton` | Reload Triton and sync all backend servers |

## Example prompts

Once connected, you can talk to the AI naturally:

> Check which translations in the `survival` collection are missing a Chinese translation, then fill them all in.

> I just added 20 new keys to my plugin. Here's the list — create them all in the `shop` collection with English and Simplified Chinese translations, then reload.

> Find any translation that contains the word "error" and update the English text to be less technical.

> Delete all keys under `debug.*` and reload when done.

> List all my languages, then go through the `default` collection and make sure every entry has a translation for all of them. Fill in anything that's missing.

## Connecting Claude Desktop

Edit `claude_desktop_config.json` and add an entry under `mcpServers`:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "triton-mcp": {
      "url": "http://your-server-ip:25580/sse"
    }
  }
}
```

If `authToken` is set in the plugin config:

```json
{
  "mcpServers": {
    "triton-mcp": {
      "url": "http://your-server-ip:25580/sse",
      "headers": {
        "Authorization": "Bearer your-token-here"
      }
    }
  }
}
```

Restart Claude Desktop after saving. The triton-mcp tools will appear in the tools list.

## Installation

1. Drop the jar into your Velocity `plugins/` folder (Triton must already be installed)
2. Start the server — a default `config.yml` is generated under `plugins/triton-mcp/`
3. Point your MCP client at `http://<host>:25580/sse`

## Configuration

```yaml
port: 25580
host: "0.0.0.0"

# Set a token if the port is exposed externally
authToken: null

serverName: "triton-mcp"
serverVersion: "1.0.0"
```

When `authToken` is set, clients must send `Authorization: Bearer <token>` with every request.

## Building

Requires the Triton v4 jars in `libs/` as compile-only dependencies (not included — build from the [v4 branch](https://github.com/tritonmc/Triton/tree/v4) or copy from your server).

```bash
./gradlew shadowJar
```

Output: `build/libs/triton-mcp-1.0.0.jar`

## Compatibility

- Velocity 3.4+
- Triton v4 (local or MySQL storage)
- Java 17+
