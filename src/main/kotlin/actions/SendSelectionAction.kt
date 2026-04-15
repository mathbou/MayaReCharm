package actions

import MayaBundle as Loc
import mayacomms.MayaCommandInterface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys

class SendSelectionAction : BaseSendAction(
    Loc.message("mayarecharm.action.SendSelectionText"),
    Loc.message("mayarecharm.action.SendSelectionDescription"), null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val sdk = getMayaSdk(e.getData(LangDataKeys.MODULE)) ?: return

        val selectionModel = e.getData(LangDataKeys.EDITOR)?.selectionModel ?: return
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

        MayaCommandInterface(sdk.port).sendCodeToMaya(selectedText!!)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
