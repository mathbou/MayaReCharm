package run

import MayaBundle as Loc
import mayacomms.MayaCommandInterface
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.python.debugger.PyDebugProcess
import resources.PythonStrings
import java.net.ServerSocket

class MayaReCharmDebugProcess(
    session: XDebugSession,
    serverSocket: ServerSocket,
    executionConsole: ExecutionConsole,
    processHandler: ProcessHandler?,
    port: Int,
    private val pid: Int
) : PyDebugProcess(session, serverSocket, executionConsole, processHandler, false) {
    private val mayaCommand = MayaCommandInterface(port)

    override fun getConnectionMessage(): String {
        return Loc.message("mayarecharm.debugproc.ConnectionMessage", pid.toString())
    }

    override fun getConnectionTitle(): String {
        return Loc.message("mayarecharm.debugproc.ConnectionTitle")
    }

    override fun afterConnect() {
        if (!isConnected) {
            printToConsole(Loc.message("mayarecharm.debugproc.FailedToConnect"), ConsoleViewContentType.SYSTEM_OUTPUT)
            return
        }
    }

    override fun disconnect() {
        super.disconnect()
        mayaCommand.sendCodeToMaya(PythonStrings.STOPTRACE.message)
    }
}
