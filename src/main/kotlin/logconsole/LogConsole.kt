package logconsole

import com.intellij.diagnostic.logging.DefaultLogFilterModel
import com.intellij.diagnostic.logging.LogConsoleImpl
import com.intellij.diagnostic.logging.LogConsolePreferences
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.search.GlobalSearchScope
import mayacomms.MayaCommandInterface
import java.io.File
import java.nio.charset.Charset
import MayaBundle as Loc

class ClearConsoleAndLogFileAction(private val logConsole: LogConsole) :
    ClearConsoleAction() {

    override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        logConsole.clearLogFile()
    }
}

class ConnectToMayaLogAction(
    private val logConsole: LogConsole,
    private val port: Int
) : AnAction(
    Loc.message("mayarecharm.action.ConnectLogText"),
    Loc.message("mayarecharm.action.ConnectLogDescription"),
    IconLoader.getIcon("/icons/MayaReCharm_Action.png", ConnectToMayaLogAction::class.java)
) {

    override fun actionPerformed(e: AnActionEvent) {
        MayaCommandInterface(port).connectMayaLog()
        logConsole.reloadLogFile()
    }
}

class LogConsole(
    private val project: Project,
    private val file: File,
    private val port: Int,
    charset: Charset,
    skippedContents: Long,
    title: String,
    buildInActions: Boolean,
    searchScope: GlobalSearchScope?
) : LogConsoleImpl(project, file, charset, skippedContents, title, buildInActions, searchScope) {

    init {
        super.setFilterModel(object : DefaultLogFilterModel(project) {
            override fun processLine(line: String?): MyProcessingResult {
                line ?: return MyProcessingResult(ProcessOutputTypes.STDOUT, false, null)
                return super.processLine(line)
            }
        })
    }

    override fun addMessage(text: String?) {
        var msg: String = text ?: ""
        val logType = LogConsolePreferences.getType(msg)

        if (logType?.startsWith("WARN") ?: false) {
            msg = "\u001B[93m$msg\u001B[0m"
        }
        else if (logType?.startsWith("DEBUG") ?: false) {
            msg = "\u001B[96m$msg\u001B[0m"
        }

        super.addMessage(msg)
    }

    override fun isActive(): Boolean {
        return true
    }

    fun clearLogFile() {
        file.writeText("")
    }

    fun reloadLogFile() {
        activate()
    }

    override fun getOrCreateActions(): ActionGroup {
        val group: DefaultActionGroup = super.getOrCreateActions() as DefaultActionGroup

        val clearAllAction = ClearConsoleAndLogFileAction(this)
        val originalClearAllAction: AnAction = group.childActionsOrStubs.last { it is ClearConsoleAction }

        group.replaceAction(originalClearAllAction, clearAllAction)

        group.addAction(ConnectToMayaLogAction(this, port), Constraints.FIRST)

        return group as ActionGroup
    }
}
