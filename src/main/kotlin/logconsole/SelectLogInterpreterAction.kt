package logconsole

import MayaBundle as Loc
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.PrimaryModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.jetbrains.python.icons.PythonIcons
import flavors.INSTANCE
import settings.ApplicationSettings
import settings.ProjectSettings

class SelectLogInterpreterAction(
    private val project: Project,
    private val sdkPath: String,
) : DumbAwareAction(
    INSTANCE.getYear(sdkPath)?.let { Loc.message("mayarecharm.logwindow.SelectInterpreterText", it) }
        ?: Loc.message("mayarecharm.logwindow.SelectInterpreterTextUnknown"),
    Loc.message("mayarecharm.logwindow.SelectInterpreterDescription"),
    IconLoader.getIcon("/icons/com/jetbrains/pythonCore/python/expui/remoteInterpreter.svg", PythonIcons::class.java)
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !project.isDisposed
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (!selectLogInterpreter(project, sdkPath)) {
            Messages.showErrorDialog(
                project,
                Loc.message("mayarecharm.logwindow.SelectInterpreterFailed"),
                Loc.message("mayarecharm.logwindow.SelectInterpreterErrorTitle")
            )
        }
    }
}

internal fun selectLogInterpreter(project: Project, sdkPath: String): Boolean {
    if (project.isDisposed) return false
    val settings = ApplicationSettings.INSTANCE
    if (sdkPath !in settings.mayaSdkMapping) return false
    val sdk = settings.findByPath(sdkPath) ?: return false
    val modules = ModuleManager.getInstance(project).modules.filterNot { it.isDisposed }
    val primaryModule = PrimaryModuleManager.findPrimaryModule(project)?.takeIf { it in modules }
        ?: modules.singleOrNull()
    ApplicationManager.getApplication().runWriteAction {
        ProjectRootManager.getInstance(project).projectSdk = sdk
        primaryModule?.let { ModuleRootModificationUtil.setModuleSdk(it, sdk) }
    }
    ProjectSettings.getInstance(project).selectedSdkName = sdkPath
    return true
}
