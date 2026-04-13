package settings.ui

import MayaBundle as Loc
import flavors.MayaSdkFlavor

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.JBUI
import com.jetbrains.python.sdk.PythonSdkType
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import java.io.File
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Custom dialog for adding a Maya Python SDK.
 *
 * Bypasses PyCharm's standard file chooser which uses VirtualEnvReader.findPythonInPythonRoot()
 * (rejects "mayapy.exe" because its regex only accepts python*.exe / pypy*.exe).
 *
 * Instead, we use our own FileChooserDescriptor that filters for mayapy executables,
 * then create the SDK programmatically via SdkConfigurationUtil which validates through
 * PythonSdkFlavor.getFlavor() → MayaSdkFlavor.isValidSdkPath() — which works correctly.
 */
class MayaSdkAddDialog(private val project: Project) : DialogWrapper(project, false) {

    private val myPanel = JPanel(GridBagLayout())

    // ── Detected installations dropdown ──────────────────────────────────
    private val detectedModel = DefaultComboBoxModel<DetectedMaya>()
    private val detectedCombo = ComboBox(detectedModel).apply {
        addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val item = e.item as? DetectedMaya ?: return@addItemListener
                if (item.path != null) {
                    pathField.text = item.path.toString()
                }
            }
        }
    }

    // ── Path field with browse button ────────────────────────────────────
    private val pathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, createMayapyChooserDescriptor())
    }

    private var createdSdk: Sdk? = null

    init {
        title = Loc.message("mayarecharm.sdkadd.AddMayaSdk")

        // Populate auto-detected Maya installations
        populateDetected()

        init()

        with(GridBagConstraints()) {
            insets = JBUI.insets(4)
            gridx = 0
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            gridwidth = 1
            anchor = GridBagConstraints.WEST

            // Row 0: Detected installations
            weightx = 0.0
            myPanel.add(JLabel(Loc.message("mayarecharm.sdkadd.Detected"), JLabel.RIGHT), this)

            gridx = 1
            weightx = 1.0
            myPanel.add(detectedCombo, this)

            // Row 1: Path field
            gridy = 1
            gridx = 0
            weightx = 0.0
            myPanel.add(JLabel(Loc.message("mayarecharm.sdkadd.InterpreterPath"), JLabel.RIGHT), this)

            gridx = 1
            weightx = 1.0
            myPanel.add(pathField, this)
        }

        // Pre-fill with first detected path
        (detectedModel.getElementAt(0))?.path?.let {
            pathField.text = it.toString()
        }
    }

    override fun createCenterPanel(): JComponent = myPanel

    override fun doValidate(): ValidationInfo? {
        val path = pathField.text.trim()
        if (path.isEmpty()) {
            return ValidationInfo(Loc.message("mayarecharm.sdkadd.PathEmpty"), pathField)
        }

        val file = File(path)
        if (!file.isFile) {
            return ValidationInfo(Loc.message("mayarecharm.sdkadd.PathNotFound"), pathField)
        }

        if (!MayaSdkFlavor.isValidMayapyPath(path)) {
            return ValidationInfo(Loc.message("mayarecharm.sdkadd.NotMayapy"), pathField)
        }

        // Check not already registered
        val existing = ProjectJdkTable.getInstance().allJdks.any {
            it.homePath?.equals(path, ignoreCase = true) == true
        }
        if (existing) {
            return ValidationInfo(Loc.message("mayarecharm.sdkadd.AlreadyRegistered"), pathField)
        }

        return null
    }

    override fun doOKAction() {
        val path = pathField.text.trim()

        // Create the SDK programmatically.
        // SdkConfigurationUtil.createAndAddSDK validates via PythonSdkType.isValidSdkHome()
        // which calls PythonSdkFlavor.getFlavor(path) → our MayaSdkFlavor.isValidSdkPath()
        createdSdk = SdkConfigurationUtil.createAndAddSDK(path, PythonSdkType.getInstance())

        if (createdSdk == null) {
            setErrorText(Loc.message("mayarecharm.sdkadd.FailedToCreate"), pathField)
            return
        }

        super.doOKAction()
    }

    /**
     * Returns the created SDK, or null if the dialog was cancelled or creation failed.
     */
    fun getCreatedSdk(): Sdk? = createdSdk

    // ── Private helpers ──────────────────────────────────────────────────

    private fun populateDetected() {
        val detected = MayaSdkFlavor.discoverMayaInstallations()

        // Filter out already-registered SDKs
        val registeredPaths = ProjectJdkTable.getInstance().allJdks
            .mapNotNull { it.homePath?.lowercase() }
            .toSet()

        val available = detected.filter { it.toString().lowercase() !in registeredPaths }

        if (available.isEmpty()) {
            detectedModel.addElement(DetectedMaya(Loc.message("mayarecharm.sdkadd.NoDetected"), null))
        } else {
            for (path in available) {
                val label = buildMayaLabel(path)
                detectedModel.addElement(DetectedMaya(label, path))
            }
        }
    }

    /**
     * Build a human-readable label like "Maya 2025 — C:\Program Files\Autodesk\Maya2025\bin\mayapy.exe"
     */
    private fun buildMayaLabel(path: Path): String {
        val pathStr = path.toString()
        // Try to extract version from path (e.g., "Maya2025")
        val versionMatch = Regex("(?i)maya\\s?(\\d{4})").find(pathStr)
        return if (versionMatch != null) {
            "Maya ${versionMatch.groupValues[1]} \u2014 $pathStr"
        } else {
            pathStr
        }
    }

    /**
     * Creates a FileChooserDescriptor that accepts mayapy executables.
     * This is our own descriptor that does NOT use VirtualEnvReader.
     * Uses [FileChooserDescriptor.withFileFilter] instead of overriding deprecated isFileSelectable/isFileVisible.
     */
    private fun createMayapyChooserDescriptor(): FileChooserDescriptor {
        return FileChooserDescriptor(true, true, false, false, false, false)
            .withTitle(Loc.message("mayarecharm.sdkadd.BrowseTitle"))
            .withDescription(Loc.message("mayarecharm.sdkadd.BrowseDescription"))
            .withFileFilter { file ->
                file.isDirectory || file.nameWithoutExtension.equals("mayapy", ignoreCase = true)
            }
            .also { descriptor ->
                // Start browsing from a sensible default directory
                getDefaultBrowseRoot()?.let { root ->
                    LocalFileSystem.getInstance().findFileByPath(root)?.let {
                        descriptor.roots = listOf(it)
                    }
                }
            }
    }

    private fun getDefaultBrowseRoot(): String? {
        if (SystemInfo.isWindows) {
            val pf = System.getenv("ProgramFiles") ?: return null
            val autodesk = File(pf, "Autodesk")
            return if (autodesk.isDirectory) autodesk.absolutePath else pf
        }
        if (SystemInfo.isMac) return "/Applications/Autodesk"
        if (SystemInfo.isLinux) return "/usr/autodesk"
        return null
    }

    /**
     * Simple data class for the detected combo box items.
     */
    private data class DetectedMaya(val label: String, val path: Path?) {
        override fun toString(): String = label
    }
}



