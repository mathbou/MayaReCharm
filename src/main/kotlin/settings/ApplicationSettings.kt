package settings

import flavors.MayaSdkFlavor
import mayacomms.mayaFromMayaPy

import com.intellij.openapi.components.*
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import java.util.*

typealias SdkPortMap = MutableMap<String, ApplicationSettings.SdkInfo>

private val portRange = (4434..4534).toSet()

@State(
    name = "MCAppSettings",
    storages = [Storage(value = "mayarecharm.settings.xml", roamingType = RoamingType.DISABLED)]
)
class ApplicationSettings : PersistentStateComponent<ApplicationSettings.State> {
    data class SdkInfo(var mayaPyPath: String = "", var port: Int = -1) {
        val mayaPath: String
            get() = mayaFromMayaPy(mayaPyPath) ?: ""

        val sdk: Sdk?
            get() = INSTANCE.findByPath(mayaPyPath)
    }

    data class State(var mayaSdkMapping: SdkPortMap = mutableMapOf())

    private var myState = State()

    companion object {
        val INSTANCE: ApplicationSettings
            get() = service()
    }

    init {
        reloadMayaSdkMapping()
    }

    fun findByPath(path: String): Sdk? {
        return ProjectJdkTable.getInstance().allJdks.firstOrNull { sdk -> sdk.homePath == path }
    }

    var mayaSdkMapping: SdkPortMap
        get() = myState.mayaSdkMapping
        set(value) {
            myState.mayaSdkMapping = value
        }

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        reloadMayaSdkMapping(state.mayaSdkMapping)
    }

    fun refreshPythonSdks() {
        reloadMayaSdkMapping(mayaSdkMapping)
    }

    private fun reloadMayaSdkMapping(savedMapping: Map<String, SdkInfo> = emptyMap()) {
        val reloadedMapping = mutableMapOf<String, SdkInfo>()

        for (path in getRegisteredMayaSdkPaths()) {
            reloadedMapping[path] = savedMapping[path]?.copy(mayaPyPath = path) ?: SdkInfo(path, -1)
        }

        mayaSdkMapping.clear()
        mayaSdkMapping.putAll(reloadedMapping)
        assignEmptyPorts()
    }

    private fun getRegisteredMayaSdkPaths(): List<String> {
        return ProjectJdkTable.getInstance().allJdks
            .mapNotNull { sdk -> sdk.homePath }
            .filter(MayaSdkFlavor::isValidMayaSdkPath)
            .distinct()
    }

    private fun assignEmptyPorts() {
        val usedPorts = mayaSdkMapping.map { it.value.port }.filter { it > 0 }.toSet()
        val freePorts = PriorityQueue((portRange - usedPorts).sorted())

        for (key in mayaSdkMapping.filter { it.value.port < 0 }.keys) {
            mayaSdkMapping[key]!!.port = freePorts.remove()
        }
    }

    fun getUnusedPort(): Int {
        val usedPorts = mayaSdkMapping.map { it.value.port }.filter { it > 0 }.toSet()
        val freePorts = PriorityQueue((portRange - usedPorts).sorted())
        return freePorts.remove()
    }
}
