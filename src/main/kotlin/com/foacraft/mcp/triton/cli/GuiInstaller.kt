package com.foacraft.mcp.triton.cli

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingWorker
import javax.swing.UIManager

class GuiInstaller {

    private lateinit var frame: JFrame
    private lateinit var addressField: JTextField
    private lateinit var logArea: JTextArea

    // Status labels — one row per client
    private lateinit var codeStatusLabel: JLabel
    private lateinit var desktopStatusLabel: JLabel

    private lateinit var installBtn: JButton
    private lateinit var uninstallBtn: JButton
    private lateinit var testBtn: JButton

    fun show() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) {}

        frame = JFrame("triton-mcp Installer")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.minimumSize = Dimension(540, 440)

        val root = JPanel(BorderLayout(0, 0))
        root.border = BorderFactory.createEmptyBorder(14, 16, 14, 16)

        root.add(buildStatusPanel(), BorderLayout.NORTH)
        root.add(buildControlPanel(),BorderLayout.CENTER)
        root.add(buildLogPanel(),    BorderLayout.SOUTH)

        frame.contentPane = root
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        refreshStatus()
    }

    // ---------------------------------------------------------------------------
    // Status panel
    // ---------------------------------------------------------------------------

    private fun buildStatusPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Current Status"),
            BorderFactory.createEmptyBorder(4, 8, 8, 8)
        )

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 4, 2, 4)
        }

        fun row(row: Int, label: String): JLabel {
            gbc.gridy = row
            gbc.gridx = 0; gbc.weightx = 0.0
            panel.add(JLabel(label).also { it.font = it.font.deriveFont(Font.BOLD) }, gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            val value = JLabel("…")
            panel.add(value, gbc)
            return value
        }

        codeStatusLabel    = row(0, "Claude Code:")
        desktopStatusLabel = row(1, "Claude Desktop:")

        return panel
    }

    // ---------------------------------------------------------------------------
    // Control panel
    // ---------------------------------------------------------------------------

    private fun buildControlPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(12, 0, 12, 0)

        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        // Address row
        gbc.gridy = 0; gbc.gridx = 0; gbc.weightx = 0.0
        panel.add(JLabel("Server address:"), gbc)

        addressField = JTextField(22)
        addressField.toolTipText = "e.g.  192.168.1.10:25580"
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(addressField, gbc)

        // Buttons row
        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))

        installBtn   = JButton("Install")
        uninstallBtn = JButton("Uninstall")
        testBtn      = JButton("Test Connection")

        installBtn.addActionListener   { onInstall() }
        uninstallBtn.addActionListener { onUninstall() }
        testBtn.addActionListener      { onTest() }

        btnPanel.add(installBtn)
        btnPanel.add(uninstallBtn)
        btnPanel.add(testBtn)

        gbc.gridy = 1; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(btnPanel, gbc)

        return panel
    }

    // ---------------------------------------------------------------------------
    // Log panel
    // ---------------------------------------------------------------------------

    private fun buildLogPanel(): JPanel {
        logArea = JTextArea(8, 0)
        logArea.isEditable = false
        logArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        logArea.margin = Insets(4, 6, 4, 6)

        val scroll = JScrollPane(logArea)
        scroll.border = BorderFactory.createTitledBorder("Log")
        scroll.preferredSize = Dimension(0, 160)

        val panel = JPanel(BorderLayout())
        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    // ---------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------

    private fun onInstall() {
        val target = addressField.text.trim()
        if (target.isBlank()) {
            log("⚠  Please enter a server address.")
            return
        }
        val url = normaliseUrl(target)
        log("Installing triton-mcp → $url …")
        setButtonsEnabled(false)

        worker {
            val r1 = writeClaudeCodeConfig(url)
            val r2 = writeClaudeDesktopConfig(url)
            listOf(r1, r2)
        } then { lines ->
            lines.forEach { log(it) }
            log("Done. Restart Claude Desktop if it is running.")
            refreshStatus()
            setButtonsEnabled(true)
        }
    }

    private fun onUninstall() {
        log("Uninstalling triton-mcp …")
        setButtonsEnabled(false)

        worker {
            listOf(removeFromClaudeCodeConfig(), removeFromClaudeDesktopConfig())
        } then { lines ->
            lines.forEach { log(it) }
            log("Done.")
            refreshStatus()
            setButtonsEnabled(true)
        }
    }

    private fun onTest() {
        val target = addressField.text.trim()
        if (target.isBlank()) {
            log("⚠  Please enter a server address to test.")
            return
        }
        val url = normaliseUrl(target)
        log("Testing connection to $url …")
        setButtonsEnabled(false)
        frame.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)

        worker {
            testConnection(url)
        } then { (ok, msg) ->
            log(if (ok) "✓  $msg" else "✗  $msg")
            frame.cursor = Cursor.getDefaultCursor()
            setButtonsEnabled(true)
        }
    }

    // ---------------------------------------------------------------------------
    // Status refresh
    // ---------------------------------------------------------------------------

    private fun refreshStatus() {
        val status = readInstallStatus()

        fun label(url: String?, supported: Boolean = true): Pair<String, Color> = when {
            !supported          -> "Not supported on this OS" to Color.GRAY
            url != null         -> "Installed — ${urlToAddress(url)}" to Color(0, 140, 0)
            else                -> "Not installed" to Color.GRAY
        }

        val (codeText, codeColor) = label(status.claudeCodeUrl)
        val (deskText, deskColor) = label(status.claudeDesktopUrl, status.desktopSupported)

        codeStatusLabel.text       = codeText
        codeStatusLabel.foreground = codeColor
        desktopStatusLabel.text       = deskText
        desktopStatusLabel.foreground = deskColor

        // Pre-populate address field if something is installed and field is empty
        if (addressField.text.isBlank() && status.installedAddress != null) {
            addressField.text = status.installedAddress
        }

        uninstallBtn.isEnabled = status.isInstalled
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun log(msg: String) {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        logArea.append("[$time] $msg\n")
        logArea.caretPosition = logArea.document.length
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        installBtn.isEnabled   = enabled
        testBtn.isEnabled      = enabled
        if (enabled) uninstallBtn.isEnabled = readInstallStatus().isInstalled
        else         uninstallBtn.isEnabled = false
    }

    /** Tiny inline SwingWorker DSL: `worker { background() } then { result -> edt() }` */
    private fun <T> worker(background: () -> T): WorkerBuilder<T> = WorkerBuilder(background)

    private inner class WorkerBuilder<T>(private val bg: () -> T) {
        infix fun then(edt: (T) -> Unit) {
            object : SwingWorker<T, Unit>() {
                override fun doInBackground(): T = bg()
                override fun done() = edt(get())
            }.execute()
        }
    }
}
