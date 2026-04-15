package debugattach

import MayaBundle as Loc
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyLocalPositionConverter
import com.jetbrains.python.debugger.attach.PyAttachToProcessDebugRunner
import run.MayaReCharmDebugProcess
import settings.ApplicationSettings
import java.io.IOException
import java.net.ServerSocket

class MayaAttachToProcessDebugRunner(
    private val project: Project,
    private val pid: Int,
    private val sdk: Sdk?,
    private val mayaSdk: ApplicationSettings.SdkInfo
) : PyAttachToProcessDebugRunner(project, pid, sdk) {

    override fun launch(): XDebugSession? {
        FileDocumentManager.getInstance().saveAllDocuments()
        return launchRemoteDebugServer()
    }

    private fun getDebuggerSocket(): ServerSocket? {
        var portSocket: ServerSocket? = null

        try {
            portSocket = ServerSocket(0)
        } catch (e: IOException) {
            e.printStackTrace()
            Messages.showErrorDialog(
                Loc.message("mayarecharm.debugattachproc.FailedFindPort"),
                Loc.message("mayarecharm.debugattachproc.FailedFindPortTitle")
            )
        }
        return portSocket
    }

    private fun launchRemoteDebugServer(): XDebugSession? {
        val serverSocket = getDebuggerSocket() ?: return null
        val state = MayaAttachToProcessCliState.create(project, sdk!!, serverSocket.localPort, pid, mayaSdk)
        val result = state.execute(state.environment.executor, this)

        val icon = IconLoader.getIcon("/icons/MayaReCharm_ToolWindow.png", this::class.java)

        return XDebuggerManager.getInstance(project)
            .newSessionBuilder(object : XDebugProcessStarter() {
                override fun start(dSession: XDebugSession): XDebugProcess {
                    val process = MayaReCharmDebugProcess(
                        dSession,
                        serverSocket,
                        result.executionConsole,
                        result.processHandler,
                        null,
                        pid
                    )
                    process.positionConverter = PyLocalPositionConverter()
                    return process
                }
            })
            .sessionName(pid.toString())
            .icon(icon)
            .showTab(true)
            .startSession()
            .session
    }
}
