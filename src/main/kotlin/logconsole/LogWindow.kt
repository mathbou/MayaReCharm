package logconsole

import MayaBundle as Loc
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import mayacomms.LOG_FILENAME_STRING
import settings.ApplicationSettings
import settings.ProjectSettings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.ui.popup.JBPopupFactory
import flavors.MayaSdkFlavor
import java.io.File
import java.nio.charset.Charset
import java.awt.event.MouseEvent

class LogWindow : ToolWindowFactory, DumbAware {
    private val sdkPathKey = Key.create<String>("MayaReCharm.LogWindow.sdkPath")

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val projectSettings = ProjectSettings.getInstance(project)
        val selectedSdkPath = projectSettings.selectedSdkName

        val contentManager = toolWindow.contentManager
        val sdkEntries = getSortedSdkEntries()

        var selectedContent: Content? = null
        var selectedConsole: LogConsole? = null

        for ((sdkPath, sdkInfo) in sdkEntries) {
            val (content, console) = addSdkTab(project, contentManager, sdkPath, sdkInfo, select = false)

            if (selectedSdkPath != null && selectedSdkPath == sdkPath) {
                selectedContent = content
                selectedConsole = console
            }
        }

        selectedContent?.let(contentManager::setSelectedContent)

        val reopenAction = ReopenLogTabAction(project, toolWindow)
        (toolWindow as? ToolWindowEx)?.setTabActions(reopenAction)

        toolWindow.setAvailable(true, null)
        val consoleToActivate = selectedConsole
        if (consoleToActivate != null) {
            toolWindow.activate(consoleToActivate::activate)
        }
    }

    private fun getSortedSdkEntries(): List<Map.Entry<String, ApplicationSettings.SdkInfo>> {
        return ApplicationSettings.INSTANCE.mayaSdkMapping.entries
            .sortedWith(compareBy<Map.Entry<String, ApplicationSettings.SdkInfo>> { it.value.port }.thenBy { it.key })
    }

    private fun addSdkTab(
        project: Project,
        contentManager: ContentManager,
        sdkPath: String,
        sdkInfo: ApplicationSettings.SdkInfo,
        select: Boolean
    ): Pair<Content, LogConsole> {
        val port = sdkInfo.port
        val year = MayaSdkFlavor.getYear(sdkPath) ?: "-"
        val console = createConsole(project, port)
        val content = ContentFactory.getInstance().createContent(console.component, "Maya $year ($port)", false)
        content.putUserData(sdkPathKey, sdkPath)
        contentManager.addContent(content)

        if (select) {
            contentManager.setSelectedContent(content)
            console.activate()
        }

        return content to console
    }

    private fun isSdkTabOpen(contentManager: ContentManager, sdkPath: String): Boolean {
        return contentManager.contents.any { it.getUserData(sdkPathKey) == sdkPath }
    }

    private fun createConsole(project: Project, port: Int): LogConsole {
        val mayaLogPath = File(System.getProperty("java.io.tmpdir"), String.format(LOG_FILENAME_STRING, port))
        return LogConsole(
            project,
            mayaLogPath,
            port,
            Charset.defaultCharset(),
            0L,
            "MayaLog",
            true,
            GlobalSearchScope.allScope(project)
        )
    }

    private inner class ReopenLogTabAction(
        private val project: Project,
        private val toolWindow: ToolWindow
    ) : DumbAwareAction(
        Loc.message("mayarecharm.logwindow.ReopenTabActionText"),
        Loc.message("mayarecharm.logwindow.ReopenTabActionDescription"),
        AllIcons.General.ArrowDown
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
            val hasMissingTabs = getSortedSdkEntries()
                .any { (sdkPath, _) -> !isSdkTabOpen(toolWindow.contentManager, sdkPath) }
            e.presentation.isVisible = hasMissingTabs
            e.presentation.isEnabled = hasMissingTabs
        }

        override fun actionPerformed(e: AnActionEvent) {
            val contentManager = toolWindow.contentManager
            val missingEntries = getSortedSdkEntries()
                .filter { (sdkPath, _) -> !isSdkTabOpen(contentManager, sdkPath) }

            if (missingEntries.isEmpty()) {
                return
            }

            val menu = DefaultActionGroup()
            for ((sdkPath, sdkInfo) in missingEntries) {
                val port = sdkInfo.port
                val year = MayaSdkFlavor.getYear(sdkPath) ?: "-"
                val label = "Maya $year ($port)"

                menu.add(object : DumbAwareAction(label) {
                    override fun actionPerformed(e: AnActionEvent) {
                        addSdkTab(project, contentManager, sdkPath, sdkInfo, select = true)
                    }
                })
            }

            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                Loc.message("mayarecharm.logwindow.ReopenTabDialogTitle"),
                menu,
                e.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )

            val mousePoint = (e.inputEvent as? MouseEvent)?.let { RelativePoint(it.component, it.point) }
            if (mousePoint != null) {
                popup.show(mousePoint)
            } else {
                popup.show(RelativePoint.getCenterOf(toolWindow.component))
            }
        }
    }
}
