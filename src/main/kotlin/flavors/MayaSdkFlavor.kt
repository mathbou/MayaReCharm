package flavors

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon


class MayaSdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {

    companion object {
        @JvmStatic
        val INSTANCE: MayaSdkFlavor = MayaSdkFlavor()

        // ── Static helpers usable from dialogs ───────────────────────────

        /**
         * Validates that the given path points to a mayapy executable.
         */
        @JvmStatic
        fun isValidMayapyPath(pathStr: String): Boolean = INSTANCE.isValidSdkPath(pathStr)

        /**
         * Discovers Maya installations on the local system.
         * Returns paths to mayapy executables.
         */
        @JvmStatic
        fun discoverMayaInstallations(): Collection<Path> {
            val results = mutableListOf<Path>()
            try {
                if (SystemInfo.isWindows) INSTANCE.discoverWindows(results)
                else if (SystemInfo.isLinux) INSTANCE.discoverLinux(results)
            } catch (_: Exception) {
                // never let a scan failure propagate
            }
            return results
        }
    }

    override fun isApplicable(): Boolean = true

    override fun getName(): String = "Maya Python"

    // ── Auto-detection of Maya installations ─────────────────────────────

    override fun suggestLocalHomePathsImpl(
        module: Module?,
        context: UserDataHolder?
    ): Collection<Path> {
        val results = mutableListOf<Path>()
        try {
            if (SystemInfo.isWindows) discoverWindows(results)
            else if (SystemInfo.isLinux) discoverLinux(results)
        } catch (_: Exception) {
            // never let a scan failure propagate
        }
        return results
    }

    // ── Flavor validation ────────────────────────────────────────────────

    override fun isValidSdkPath(pathStr: String): Boolean {
        val file = File(pathStr)
        return isMayapyExecutable(file) || (file.isDirectory && findMayapyInDirectory(file) != null)
    }

    override fun getIcon(): Icon = IconLoader.getIcon("/icons/MayaReCharm_Action.png", this::class.java)

    override fun getSdkPath(path: VirtualFile): VirtualFile? {
        if (!path.isDirectory) {
            return path.takeIf { isMayapyExecutable(File(it.path)) }
        }

        val candidates = listOf(
            "mayapy.exe",
            "mayapy",
            "bin/mayapy.exe",
            "bin/mayapy",
            "Contents/bin/mayapy"
        )

        return candidates.mapNotNull { path.findFileByRelativePath(it) }
            .firstOrNull { !it.isDirectory }
            ?: path
    }

    override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java

    private fun isMayapyExecutable(file: File): Boolean =
        file.isFile && file.nameWithoutExtension.equals("mayapy", ignoreCase = true)

    private fun findMayapyInDirectory(directory: File): File? {
        val candidates = listOf(
            File(directory, "mayapy.exe"),
            File(directory, "mayapy"),
            File(directory, "bin/mayapy.exe"),
            File(directory, "bin/mayapy"),
            File(directory, "Contents/bin/mayapy")
        )
        return candidates.firstOrNull { it.isFile }
    }

    // ── Platform-specific Maya discovery ─────────────────────────────────

    /**
     * Windows: Maya lives under "C:\Program Files\Autodesk\Maya20XX"
     * or directly "C:\Program Files\Maya20XX".
     * The binary is in the "bin" sub-directory.
     */
    private fun discoverWindows(out: MutableList<Path>) {
        val roots = listOfNotNull(
            System.getenv("ProgramFiles"),       // typically C:\Program Files
            System.getenv("ProgramFiles(x86)"),
            System.getenv("ProgramW6432")
        ).distinct()

        for (root in roots) {
            // Direct: <root>/Maya20XX/bin/mayapy.exe
            scanForMayapy(Path.of(root), "maya", "bin/mayapy.exe", out)
            // Autodesk sub-folder: <root>/Autodesk/Maya20XX/bin/mayapy.exe
            val autodesk = Path.of(root, "Autodesk")
            if (Files.isDirectory(autodesk)) {
                scanForMayapy(autodesk, "maya", "bin/mayapy.exe", out)
            }
        }

        // MAYA_LOCATION environment variable
        System.getenv("MAYA_LOCATION")?.let { loc ->
            addIfExists(Path.of(loc, "bin", "mayapy.exe"), out)
        }
    }

    /**
     * Linux: /usr/autodesk/maya20XX/bin/mayapy
     */
    private fun discoverLinux(out: MutableList<Path>) {
        val autodesk = Path.of("/usr/autodesk")
        if (Files.isDirectory(autodesk)) {
            scanForMayapy(autodesk, "maya", "bin/mayapy", out)
        }
        System.getenv("MAYA_LOCATION")?.let { loc ->
            addIfExists(Path.of(loc, "bin", "mayapy"), out)
        }
    }

    /** List children of [parent] whose name starts with [prefix] (case-insensitive) and check for [relBinary]. */
    private fun scanForMayapy(parent: Path, prefix: String, relBinary: String, out: MutableList<Path>) {
        if (!Files.isDirectory(parent)) return
        try {
            Files.newDirectoryStream(parent) { entry ->
                Files.isDirectory(entry) && entry.fileName.toString().lowercase().startsWith(prefix)
            }.use { stream ->
                for (dir in stream) {
                    addIfExists(dir.resolve(relBinary), out)
                }
            }
        } catch (_: Exception) {
            // ignore I/O errors during scan
        }
    }

    private fun addIfExists(path: Path, out: MutableList<Path>) {
        if (Files.isRegularFile(path)) out.add(path)
    }
}

class MayaFlavorProvider : PythonFlavorProvider {
    override fun getFlavor(): MayaSdkFlavor = MayaSdkFlavor.INSTANCE
}