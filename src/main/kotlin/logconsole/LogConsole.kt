package logconsole

import com.intellij.diagnostic.logging.DefaultLogFilterModel
import com.intellij.diagnostic.logging.IndependentLogFilter
import com.intellij.diagnostic.logging.LogFilter
import com.intellij.diagnostic.logging.LogConsoleBase
import com.intellij.diagnostic.logging.LogConsolePreferences
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.search.GlobalSearchScope
import mayacomms.MayaCommandInterface
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Arrays
import MayaBundle as Loc

private enum class MayaLogLevelFilter {
    ALL,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

enum class MayaLogSeverity {
    HISTORY,
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

class MayaLogFilterModel(project: Project, tabId: String) : DefaultLogFilterModel(project) {
    private var previousSeverity: MayaLogSeverity? = null
    private val selectedFilterKey = "$SELECTED_FILTER_KEY_PREFIX.$tabId"
    private var selectedFilter: MayaLogLevelFilter = loadPersistedFilter(selectedFilterKey)

    init {
        isCheckStandartFilters = false
    }

    override fun getLogFilters(): List<LogFilter> {
        val filters = mutableListOf<LogFilter>()
        filters.add(SeverityFilter("All", MayaLogLevelFilter.ALL))
        filters.add(SeverityFilter("Debug", MayaLogLevelFilter.DEBUG))
        filters.add(SeverityFilter("Infos", MayaLogLevelFilter.INFO))
        filters.add(SeverityFilter("Warnings", MayaLogLevelFilter.WARN))
        filters.add(SeverityFilter("Errors", MayaLogLevelFilter.ERROR))
        filters.addAll(getPreferences().registeredLogFilters)
        return filters
    }

    override fun processLine(line: String): MyProcessingResult {
        val detectedSeverity = detectSeverity(line)
        val effectiveSeverity = detectedSeverity ?: previousSeverity

        if (detectedSeverity != null) {
            previousSeverity = detectedSeverity
        }

        val contentType = if (effectiveSeverity == MayaLogSeverity.ERROR) {
            ProcessOutputTypes.STDERR
        }
        else {
            ProcessOutputTypes.STDOUT
        }

        val isVisible = super.isApplicable(line) && isVisibleForSelectedFilter(effectiveSeverity)
        return MyProcessingResult(contentType, isVisible, null)
    }

    private fun isVisibleForSelectedFilter(severity: MayaLogSeverity?): Boolean {
        severity ?: return true

        return when (selectedFilter) {
            MayaLogLevelFilter.ALL -> true
            MayaLogLevelFilter.DEBUG -> severity != MayaLogSeverity.HISTORY
            MayaLogLevelFilter.INFO -> severity != MayaLogSeverity.HISTORY && severity != MayaLogSeverity.DEBUG
            MayaLogLevelFilter.WARN -> severity == MayaLogSeverity.WARNING || severity == MayaLogSeverity.ERROR
            MayaLogLevelFilter.ERROR -> severity == MayaLogSeverity.ERROR
        }
    }

    fun detectSeverity(line: String): MayaLogSeverity? {
        if (LEVEL_5_REGEX.containsMatchIn(line)) {
            return MayaLogSeverity.HISTORY
        }

        return when (LogConsolePreferences.getType(line)) {
            LogConsolePreferences.ERROR -> MayaLogSeverity.ERROR
            LogConsolePreferences.WARNING -> MayaLogSeverity.WARNING
            LogConsolePreferences.INFO -> MayaLogSeverity.INFO
            LogConsolePreferences.DEBUG -> MayaLogSeverity.DEBUG
            else -> null
        }
    }

    private inner class SeverityFilter(name: String, private val filter: MayaLogLevelFilter) : IndependentLogFilter(name) {
        override fun selectFilter() {
            selectedFilter = filter
            persistFilter(selectedFilterKey, filter)
        }

        override fun isSelected(): Boolean {
            return selectedFilter == filter
        }

        override fun isAcceptable(line: String): Boolean {
            return true
        }
    }

    private companion object {
        private const val SELECTED_FILTER_KEY_PREFIX = "mayarecharm.logconsole.selectedFilter"
        val LEVEL_5_REGEX = Regex("\\blevel\\s*5\\b", RegexOption.IGNORE_CASE)

        private fun loadPersistedFilter(selectedFilterKey: String): MayaLogLevelFilter {
            val properties = PropertiesComponent.getInstance()
            val persistedFilter = properties.getValue(selectedFilterKey)

            return persistedFilter
                ?.let { value -> MayaLogLevelFilter.entries.firstOrNull { it.name == value } }
                ?: MayaLogLevelFilter.INFO
        }

        private fun persistFilter(selectedFilterKey: String, filter: MayaLogLevelFilter) {
            PropertiesComponent.getInstance().setValue(selectedFilterKey, filter.name)
        }
    }
}

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
    project: Project,
    private val file: File,
    private val port: Int,
    private val charset: Charset,
    skippedContents: Long,
    title: String,
    buildInActions: Boolean,
    searchScope: GlobalSearchScope?
) : LogConsoleBase(
    project,
    getReader(file, charset, skippedContents),
    title,
    buildInActions,
    MayaLogFilterModel(project, port.toString()),
    searchScope ?: GlobalSearchScope.allScope(project)
) {

    private val path = file.absolutePath
    private var oldSnapshot = FileSnapshot()

    override fun getTooltip(): String {
        return path
    }

    override fun updateReaderIfNeeded(reader: BufferedReader?): BufferedReader? {
        reader ?: return null

        val snapshot = FileSnapshot()
        if (oldSnapshot.rolloverDetected(snapshot)) {
            reader.close()
            oldSnapshot = snapshot
            return BufferedReader(InputStreamReader(Files.newInputStream(file.toPath()), charset))
        }

        oldSnapshot = snapshot
        return reader
    }

    override fun getFilterModel(): MayaLogFilterModel? {
        return super.getFilterModel() as MayaLogFilterModel?
    }

    override fun addMessage(text: String?) {
        var msg: String = text ?: ""

        val logType = filterModel?.detectSeverity(msg)

        if (logType == MayaLogSeverity.WARNING) {
            msg = "\u001B[93m$msg\u001B[0m"
        }
        else if (logType == MayaLogSeverity.DEBUG) {
            msg = "\u001B[96m$msg\u001B[0m"
        }
        else if (logType == MayaLogSeverity.HISTORY) {
            msg = msg.replaceFirst("Level 5", "HISTORY")
            msg = "\u001B[95m$msg\u001B[0m"

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

    private inner class FileSnapshot {
        val length: Long = file.length()
        val firstBytes: ByteArray = ByteArray(20)

        init {
            try {
                Files.newInputStream(file.toPath()).use { stream ->
                    stream.read(firstBytes)
                }
            }
            catch (_: Exception) {
            }
        }

        fun rolloverDetected(current: FileSnapshot): Boolean {
            return current.length < length || !Arrays.equals(firstBytes, current.firstBytes)
        }
    }

    companion object {
        private fun getReader(file: File, charset: Charset, skippedContents: Long): Reader? {
            return try {
                try {
                    val inputStream = Files.newInputStream(file.toPath())
                    if (file.length() >= skippedContents) {
                        var skipped = 0L
                        while (skipped < skippedContents) {
                            skipped += inputStream.skip(skippedContents - skipped)
                        }
                    }
                    BufferedReader(InputStreamReader(inputStream, charset))
                }
                catch (_: FileNotFoundException) {
                    if (FileUtilRt.createIfNotExists(file)) {
                        BufferedReader(InputStreamReader(Files.newInputStream(file.toPath()), charset))
                    }
                    else {
                        null
                    }
                }
            }
            catch (_: Throwable) {
                null
            }
        }
    }
}
