package actions

import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.python.sdk.PythonSdkUtil
import resources.MayaNotifications
import settings.ApplicationSettings
import javax.swing.Icon

abstract class BaseSendAction(text: String, description: String, icon: Icon?) :
    DumbAwareAction(text, description, icon) {
    abstract override fun actionPerformed(e: AnActionEvent)

    override fun update(e: AnActionEvent) {
        val currentFile = e.getData(LangDataKeys.VIRTUAL_FILE)
        if (currentFile == null) {
            e.presentation.isEnabled = false
            return
        }
        val fileTypeManager = FileTypeManager.getInstance()
        val pyFileType = fileTypeManager.findFileTypeByName("Python")
        e.presentation.isEnabled = pyFileType != null && fileTypeManager.isFileOfType(currentFile, pyFileType)
    }

    protected fun getMayaSdk(module: Module?): ApplicationSettings.SdkInfo? {
        val homePath = PythonSdkUtil.findPythonSdk(module)?.homePath ?: return run {
            Notifications.Bus.notify(MayaNotifications.NO_SDK_SELECTED)
            null
        }

        return ApplicationSettings.INSTANCE.mayaSdkMapping[homePath] ?: run {
            Notifications.Bus.notify(MayaNotifications.INVALID_SDK_SELECTED)
            null
        }
    }
}
