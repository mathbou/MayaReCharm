package run

import resources.MayaNotifications
import settings.ApplicationSettings

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.impl.ProcessListUtil
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.IconLoader
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyDebugRunner
import com.jetbrains.python.debugger.PyLocalPositionConverter
import debugattach.MayaAttachToProcessCliState
import java.net.ServerSocket

class MayaReCharmDebugRunner : PyDebugRunner() {
    override fun getRunnerId(): String {
        return "MayaReCharmDebugRunner"
    }

    override fun canRun(executorId: String, runProfile: RunProfile): Boolean {
        val runConfig = runProfile as? MayaReCharmRunConfiguration ?: return false

        try {
            runConfig.checkConfiguration()
        } catch (e: RuntimeConfigurationException) {
            return false
        }

        return DefaultDebugExecutor.EXECUTOR_ID == executorId
    }

    override fun execute(environment: ExecutionEnvironment) {
        val sdks = ApplicationSettings.INSTANCE.mayaSdkMapping
        val runConfig = environment.runProfile as MayaReCharmRunConfiguration
        val sdkInfo = sdks[runConfig.mayaSdkPath] ?: return

        val process = ProcessListUtil.getProcessList().firstOrNull { it.commandLine.contains(sdkInfo.mayaPath) }
        if (process == null) {
            MayaNotifications.mayaInstanceNotFound(sdkInfo.mayaPath, environment.project)
            return
        }

        val sdk = sdkInfo.sdk
        if (sdk == null) {
            MayaNotifications.mayaInstanceNotFound(sdkInfo.mayaPath, environment.project)
            return
        }

        val serverSocket = ServerSocket(0) // port 0 forces the ServerSocket to choose its own free port
        val cliState =
            MayaAttachToProcessCliState.create(environment.project, sdk, serverSocket.localPort, process.pid, sdkInfo)
        val executionResult = cliState.execute(environment.executor, this)
        val icon = IconLoader.getIcon("/icons/MayaReCharm_ToolWindow.png", this::class.java)

        XDebuggerManager.getInstance(environment.project)
            .newSessionBuilder(object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess {
                    val debugProcess = MayaReCharmDebugProcess(
                        session,
                        serverSocket,
                        executionResult.executionConsole,
                        executionResult.processHandler,
                        runConfig,
                        process.pid
                    )
                    debugProcess.positionConverter = PyLocalPositionConverter()
                    return debugProcess
                }
            })
            .sessionName(process.pid.toString())
            .icon(icon)
            .showTab(true)
            .startSession()
            .session
    }
}
