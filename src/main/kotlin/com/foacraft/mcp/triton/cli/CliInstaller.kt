package com.foacraft.mcp.triton.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "--install" -> {
            val target = args.getOrNull(1)
            if (target == null) {
                println("Error: --install requires an <ip:port> argument.")
                println("  Example: java -jar triton-mcp.jar --install 192.168.1.10:25580")
                exitProcess(1)
            }
            cliInstall(target)
        }
        "--uninstall" -> cliUninstall()
        "--help", "-h" -> printHelp()
        null -> {
            // No arguments — launch GUI if a display is available, otherwise show help
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                javax.swing.SwingUtilities.invokeLater { GuiInstaller().show() }
            } else {
                printHelp()
            }
        }
        else -> {
            println("Unknown option: ${args[0]}")
            printHelp()
            exitProcess(1)
        }
    }
}

// ---------------------------------------------------------------------------
// CLI actions
// ---------------------------------------------------------------------------

private fun cliInstall(target: String) {
    val url = normaliseUrl(target)
    println("Installing triton-mcp → $url")
    println()
    println(writeClaudeCodeConfig(url))
    println(writeClaudeDesktopConfig(url))
    println()
    println("Done. Restart Claude Desktop if it is running.")
}

private fun cliUninstall() {
    println("Uninstalling triton-mcp…")
    println()
    println(removeFromClaudeCodeConfig())
    println(removeFromClaudeDesktopConfig())
    println()
    println("Done.")
}

// ---------------------------------------------------------------------------
// Help
// ---------------------------------------------------------------------------

private fun printHelp() {
    println("""
triton-mcp installer

Usage:
  java -jar triton-mcp.jar                        Open graphical installer (default when no args)
  java -jar triton-mcp.jar --install <ip:port>    Install MCP config for Claude Code and Claude Desktop
  java -jar triton-mcp.jar --uninstall            Remove triton-mcp from all Claude configs
  java -jar triton-mcp.jar --help                 Show this help

Examples:
  java -jar triton-mcp.jar --install 192.168.1.10:25580
  java -jar triton-mcp.jar --install my.server.com:25580
  java -jar triton-mcp.jar --uninstall

Config files written:
  Claude Code    : ~/.claude.json
  Claude Desktop : ~/Library/Application Support/Claude/claude_desktop_config.json  (macOS)
                   %APPDATA%\Claude\claude_desktop_config.json                       (Windows)

Note: If authToken is enabled on the server, edit the config files manually to add the token.
  Claude Code    : add  "headers": {"Authorization": "Bearer <token>"}
  Claude Desktop : append  "--header", "Authorization: Bearer <token>"  to the args array
    """.trimIndent())
}
