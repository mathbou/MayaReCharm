package logconsole

import com.intellij.diagnostic.logging.DefaultLogFilterModel
import com.intellij.diagnostic.logging.LogConsoleImpl
import com.intellij.diagnostic.logging.LogConsolePreferences
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.nio.charset.Charset


class MyCustomClearConsoleAction(customConsole: LogConsole?) :
    ClearConsoleAction() {
    private val myCustomConsole: LogConsole? = customConsole

    override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        myCustomConsole?.clearFile()
    }
}


class LogConsole(
    private val project: Project,
    private val file: File,
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
        var text_: String = text ?: ""
        val logType = LogConsolePreferences.getType(text_)

        if (logType?.startsWith("WARN") ?: false) {
            text_ = "\u001B[93m$text_\u001B[0m"
        }
        else if (logType?.startsWith("DEBUG") ?: false) {
            text_ = "\u001B[96m$text_\u001B[0m"
        }

        super.addMessage(text_)
    }

    override fun isActive(): Boolean {
        return true
    }

    fun clearFile() {
        file.writeText("")
    }

    override fun getOrCreateActions(): ActionGroup? {
        val group: DefaultActionGroup = super.getOrCreateActions() as DefaultActionGroup

        val clearAllAction = MyCustomClearConsoleAction(this)
        val originalClearAllAction: AnAction = group.childActionsOrStubs.last { it is ClearConsoleAction }

        group.replaceAction(originalClearAllAction, clearAllAction)

        return group as ActionGroup
    }
}
