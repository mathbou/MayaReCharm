package settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
@State(
    name = "MCProjectSettings",
    storages = [Storage(value = StoragePathMacros.WORKSPACE_FILE)]
)
class ProjectSettings(val project: Project) : PersistentStateComponent<ProjectSettings.State> {
    private val appSettings = ApplicationSettings.INSTANCE

    data class State(
        var selectedSdk: String? = null,
        var openLogSdkPaths: MutableList<String>? = null,
        var selectedLogSdk: String? = null,
        var quickSendFavorites: MutableList<String>? = null,
        var quickSendUsage: MutableMap<String, Int>? = null
    )

    private var myState = State()

    val pythonCachePath: Path = Paths.get(project.getProjectCachePath("MayaReCharm").toString(), "python")

    init {
        unpackResources()
    }

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState.selectedSdk = state.selectedSdk
        myState.openLogSdkPaths = state.openLogSdkPaths?.distinct()?.toMutableList()
        myState.selectedLogSdk = state.selectedLogSdk
        myState.quickSendFavorites = state.quickSendFavorites?.distinct()?.toMutableList()
        myState.quickSendUsage = state.quickSendUsage?.toMutableMap()
    }

    var selectedSdkName: String?
        get() = myState.selectedSdk ?: appSettings.mayaSdkMapping.keys.maxOrNull()
        set(value) {
            myState.selectedSdk = value
        }

    var openLogSdkPaths: List<String>?
        get() = myState.openLogSdkPaths?.filter { it in appSettings.mayaSdkMapping }
        set(value) {
            myState.openLogSdkPaths = value?.distinct()?.toMutableList()
        }

    var selectedLogSdkPath: String?
        get() = myState.selectedLogSdk?.takeIf { it in appSettings.mayaSdkMapping }
        set(value) {
            myState.selectedLogSdk = value
        }

    val quickSendFavorites: Set<String>
        get() = myState.quickSendFavorites.orEmpty().toSet()

    val quickSendUsage: Map<String, Int>
        get() = myState.quickSendUsage.orEmpty()

    fun toggleQuickSendFavorite(path: String) {
        val favorites = myState.quickSendFavorites ?: mutableListOf<String>().also {
            myState.quickSendFavorites = it
        }
        if (!favorites.remove(path)) favorites.add(path)
    }

    fun recordQuickSend(path: String) {
        val usage = myState.quickSendUsage ?: mutableMapOf<String, Int>().also {
            myState.quickSendUsage = it
        }
        usage[path] = (usage[path] ?: 0) + 1
    }

    private fun unpackResources() {
        val pythonCache = pythonCachePath
        pythonCache.delete(true)
        pythonCache.createDirectories()

        for (resPath in listOf("attach_pydevd.py")) {
            val res = this::class.java.classLoader.getResource("python/$resPath") ?: continue
            File("$pythonCache/$resPath").writeBytes(res.readBytes())
        }
    }

    companion object {
        fun getInstance(project: Project): ProjectSettings {
            return project.service()
        }
    }
}
