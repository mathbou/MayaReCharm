package flavors

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import java.io.File
import javax.swing.Icon

class MayaSdkFlavor private constructor() : PythonSdkFlavor<PyFlavorData.Empty>() {
    override fun isValidSdkPath(pathStr: String): Boolean {
        val name = FileUtil.getNameWithoutExtension(pathStr).lowercase()
        return name.startsWith("mayapy") || isMayaFolder(File(pathStr))
    }

    override fun getName(): String {
        return "Maya Python"
    }

    override fun getIcon(): Icon {
        return PythonPsiApiIcons.Python
    }

    override fun getSdkPath(path: VirtualFile): VirtualFile? {
        if (isMayaFolder(File(path.path))) {
            return path.findFileByRelativePath("Contents/bin/mayapy")
        }
        return path
    }

    companion object {
        const val verStringPrefix = "Python "

        val INSTANCE: MayaSdkFlavor = MayaSdkFlavor()

        private fun isMayaFolder(file: File): Boolean {
            return file.isDirectory && file.name == "Maya.app"
        }
    }
}

class MayaFlavorProvider : PythonFlavorProvider {
    override fun getFlavor(platformIndependent: Boolean): PythonSdkFlavor<PyFlavorData.Empty> = MayaSdkFlavor.INSTANCE
}
