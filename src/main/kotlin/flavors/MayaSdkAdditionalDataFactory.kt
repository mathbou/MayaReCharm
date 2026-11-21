package flavors

import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import java.nio.file.Files
import java.nio.file.Path

fun buildMayaSdkAdditionalData(workingDirectory: Path): PythonSdkAdditionalData {
    require(workingDirectory.isAbsolute && Files.isDirectory(workingDirectory)) {
        "The project working directory must be an existing absolute directory: $workingDirectory"
    }
    return PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, INSTANCE), workingDirectory)
}
