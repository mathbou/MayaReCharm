package actions

import MayaBundle as Loc
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.presentation.FilePresentationService
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.JBColor
import com.jetbrains.python.sdk.PythonSdkUtil
import mayacomms.MayaCommandInterface
import resources.MayaNotifications
import settings.ApplicationSettings
import settings.ProjectSettings
import java.awt.Component
import java.awt.FlowLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities

class QuickSendFileAction : AnAction() {
    private data class SdkChoice(val path: String, val name: String)
    private data class ProjectFiles(val files: List<VirtualFile>, val colors: Map<VirtualFile, Color>)

    private var activePickerProject: Project? = null

    init {
        templatePresentation.text = Loc.message("mayarecharm.action.QuickSendFileText")
        templatePresentation.description = Loc.message("mayarecharm.action.QuickSendFileDescription")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (activePickerProject?.isDisposed == false) return
        activePickerProject = project
        val currentSdkPath = e.getData(LangDataKeys.MODULE)?.let {
            PythonSdkUtil.findPythonSdk(it)?.homePath
        }
        showPicker(project, e.dataContext, preferredSdkPath = currentSdkPath)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun showPicker(
        project: Project,
        dataContext: DataContext,
        selectedFile: VirtualFile? = null,
        preferredSdkPath: String? = null
    ) {
        if (project.isDisposed) {
            clearActivePicker(project)
            return
        }

        ReadAction.nonBlocking<ProjectFiles> {
            val files = FilenameIndex.getAllFilesByExt(project, "py", GlobalSearchScope.projectScope(project))
                .filter { it.isValid && !it.isDirectory }
            val filePresentation = FilePresentationService.getInstance(project)
            val colors = files.mapNotNull { file ->
                filePresentation.getFileBackgroundColor(file)?.let { file to it }
            }.toMap()
            ProjectFiles(files, colors)
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.any()) { projectFiles ->
                showPicker(
                    project,
                    dataContext,
                    selectedFile,
                    preferredSdkPath,
                    projectFiles.files,
                    projectFiles.colors
                )
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun showPicker(
        project: Project,
        dataContext: DataContext,
        selectedFile: VirtualFile?,
        preferredSdkPath: String?,
        projectFiles: List<VirtualFile>,
        projectFileColors: Map<VirtualFile, Color>
    ) {
        if (project.isDisposed) {
            clearActivePicker(project)
            return
        }

        val applicationSettings = ApplicationSettings.INSTANCE
        val sdks = applicationSettings.mayaSdkMapping.values.mapNotNull { sdkInfo ->
            if (sdkInfo.port <= 0) return@mapNotNull null
            val sdk = applicationSettings.findByPath(sdkInfo.mayaPyPath) ?: return@mapNotNull null
            SdkChoice(sdkInfo.mayaPyPath, sdk.name)
        }.distinctBy { it.path }.sortedBy { it.name }

        if (sdks.isEmpty()) {
            clearActivePicker(project)
            Notifications.Bus.notify(MayaNotifications.NO_SDK_SELECTED)
            return
        }

        val projectSettings = ProjectSettings.getInstance(project)
        val favoritePaths = projectSettings.quickSendFavorites.toMutableSet()
        val usage = projectSettings.quickSendUsage
        val fileComparator = compareBy<VirtualFile> { if (it.path in favoritePaths) 0 else 1 }
            .thenByDescending { usage[it.path] ?: 0 }
            .thenBy { it.path }
        val orderedFiles = projectFiles.sortedWith(fileComparator).toMutableList()

        if (orderedFiles.isEmpty()) {
            clearActivePicker(project)
            JBPopupFactory.getInstance()
                .createMessage(Loc.message("mayarecharm.quickSend.NoPythonFiles"))
                .showInBestPositionFor(dataContext)
            return
        }

        val projectRoot = project.guessProjectDir()
        fun projectRelativePath(file: VirtualFile): String =
            projectRoot?.let { VfsUtilCore.getRelativePath(file, it, '/') } ?: file.name

        val sdkCombo = ComboBox(sdks.toTypedArray())
        sdkCombo.toolTipText = Loc.message("mayarecharm.quickSend.InterpreterShortcut")
        sdkCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component = super.getListCellRendererComponent(
                list, (value as? SdkChoice)?.name.orEmpty(), index, isSelected, cellHasFocus
            )
        }
        val preferredSdk = sdks.firstOrNull { it.path == preferredSdkPath }
            ?: sdks.firstOrNull { it.path == projectSettings.selectedSdkName }
            ?: sdks.first()
        sdkCombo.selectedItem = preferredSdk

        val controls = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
            add(JLabel(Loc.message("mayarecharm.quickSend.Interpreter")))
            add(sdkCombo)
        }

        val fileModel = DefaultListModel<VirtualFile>().apply {
            orderedFiles.forEach { addElement(it) }
        }
        val fileList = JBList(fileModel).apply {
            fixedCellHeight = JBUI.scale(28)
            setFocusTraversalKeysEnabled(false)
        }
        val rowPanel = JPanel(BorderLayout(JBUI.scale(6), 0))
        val favoriteStar = JButton().apply {
            isFocusable = false
            isBorderPainted = false
            isContentAreaFilled = false
            border = JBUI.Borders.empty()
            preferredSize = JBUI.size(28, 24)
        }
        val fileLabel = JLabel()
        rowPanel.add(favoriteStar, BorderLayout.WEST)
        rowPanel.add(fileLabel, BorderLayout.CENTER)
        fileList.cellRenderer = ListCellRenderer { list, file, _, isSelected, _ ->
            val fileColor = projectFileColors[file]
            val rowBackground = fileColor ?: if (isSelected) list.selectionBackground else list.background
            val isFavorite = file.path in favoritePaths
            rowPanel.background = rowBackground
            rowPanel.border = if (isSelected && fileColor != null) {
                JBUI.Borders.customLine(UIUtil.getFocusedBorderColor(), 2)
            } else {
                JBUI.Borders.empty()
            }
            favoriteStar.background = rowBackground
            favoriteStar.foreground = if (isFavorite) {
                JBColor(Color(0xB8860B), Color(0xFFD54F))
            } else if (isSelected && fileColor == null) {
                list.selectionForeground
            } else {
                list.foreground
            }
            favoriteStar.text = if (isFavorite) "★" else "☆"
            val favoriteAction = Loc.message(
                if (isFavorite) {
                    "mayarecharm.quickSend.RemoveFavorite"
                } else {
                    "mayarecharm.quickSend.AddFavorite"
                }
            )
            favoriteStar.accessibleContext.accessibleName = favoriteAction
            rowPanel.accessibleContext.accessibleName = file.name
            fileLabel.text = projectRelativePath(file)
            fileLabel.foreground = if (isSelected && fileColor == null) list.selectionForeground else list.foreground
            rowPanel.isOpaque = true
            rowPanel
        }

        val favoriteColumnWidth = JBUI.scale(32)
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event)) return
                val index = fileList.locationToIndex(event.point)
                val bounds = fileList.getCellBounds(index, index) ?: return
                if (!bounds.contains(event.point) || event.x > bounds.x + favoriteColumnWidth) return

                val file = fileList.model.getElementAt(index)
                projectSettings.toggleQuickSendFavorite(file.path)
                if (!favoritePaths.add(file.path)) favoritePaths.remove(file.path)

                val oldIndex = orderedFiles.indexOf(file)
                orderedFiles.sortWith(fileComparator)
                val newIndex = orderedFiles.indexOf(file)
                if (oldIndex != newIndex) {
                    fileModel.removeElementAt(oldIndex)
                    fileModel.add(newIndex, file)
                    fileList.setSelectedValue(file, true)
                }
                fileList.repaint()
                event.consume()
            }
        })

        val builder = PopupChooserBuilder(fileList)
        builder.setFilterAlwaysVisible(true)
        builder.setVisibleRowCount(minOf(8, orderedFiles.size))
        builder.setNamerForFiltering(::projectRelativePath)
        builder.setSouthComponent(controls)
        builder.registerKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)) {
            sdkCombo.selectedIndex = (sdkCombo.selectedIndex + 1) % sdkCombo.itemCount
        }
        builder.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                clearActivePicker(project)
            }
        })
        builder.setItemChosenCallback { file ->
            val choice = sdkCombo.selectedItem as? SdkChoice ?: return@setItemChosenCallback
            val sdkInfo = applicationSettings.mayaSdkMapping[choice.path]
            if (sdkInfo == null || sdkInfo.port <= 0) {
                Notifications.Bus.notify(MayaNotifications.INVALID_SDK_SELECTED)
                return@setItemChosenCallback
            }
            projectSettings.selectedSdkName = choice.path
            FileDocumentManager.getInstance().getDocument(file)?.let {
                FileDocumentManager.getInstance().saveDocument(it)
            }
            projectSettings.recordQuickSend(file.path)
            MayaCommandInterface(sdkInfo.port).sendFileToMaya(file.path)
        }

        val preselection =
            selectedFile?.let { candidate -> orderedFiles.firstOrNull { it.path == candidate.path } }
                ?: orderedFiles.first()
        val createdPopup = builder.createPopup()
        builder.setSelectedValue(preselection, true)
        createdPopup.showInBestPositionFor(dataContext)
    }

    private fun clearActivePicker(project: Project) {
        if (activePickerProject === project) activePickerProject = null
    }
}
