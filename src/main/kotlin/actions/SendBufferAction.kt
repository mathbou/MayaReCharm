package actions

import MayaBundle as Loc
import mayacomms.MayaCommandInterface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

class SendBufferAction : BaseSendAction(
    Loc.message("mayarecharm.action.SendDocumentText"),
    Loc.message("mayarecharm.action.SendDocumentDescription"), null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val sdk = getMayaSdk(e.getData(LangDataKeys.MODULE)) ?: return
        val data = e.getData(LangDataKeys.VIRTUAL_FILE) ?: return

        val docManager = FileDocumentManager.getInstance()
        data.let { docManager.getDocument(it) }?.also { docManager.saveDocument(it) }
        MayaCommandInterface(sdk.port).sendFileToMaya(data.path)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
