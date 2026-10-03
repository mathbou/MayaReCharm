package actions

import MayaBundle as Loc
import mayacomms.MayaCommandInterface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

class SendSelectionAction : BaseSendAction(
    Loc.message("mayarecharm.action.SendSelectionText"),
    Loc.message("mayarecharm.action.SendSelectionDescription"), null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val sdk = getMayaSdk(e.getData(LangDataKeys.MODULE)) ?: return

        val editor = e.getData(LangDataKeys.EDITOR) ?: return
        val virtualFile = e.getData(LangDataKeys.VIRTUAL_FILE) ?: return
        val selectionModel = editor.selectionModel
        val selectedText: String?

        if (selectionModel.hasSelection()) {
            selectedText = selectionModel.selectedText
        } else {
            selectionModel.selectLineAtCaret()
            if (selectionModel.hasSelection()) {
                selectedText = selectionModel.selectedText
                selectionModel.removeSelection()
            } else return
        }

        val selectionStart = selectionModel.selectionStart
        FileDocumentManager.getInstance().saveDocument(editor.document)
        val startLine = editor.document.getLineNumber(selectionStart)
        MayaCommandInterface(sdk.port).sendSelectionToMaya(selectedText!!, virtualFile.path, startLine)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
