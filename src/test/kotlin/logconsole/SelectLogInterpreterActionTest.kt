package logconsole

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.PrimaryModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import flavors.buildMayaSdkAdditionalData
import settings.ApplicationSettings
import settings.ProjectSettings
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.awt.image.BufferedImage
import javax.swing.Icon

class SelectLogInterpreterActionTest : BasePlatformTestCase() {
    fun testConsoleToolbarContainsInterpreterAction() {
        val log = Files.createTempFile("maya-log", ".txt")
        try {
            val console = LogConsole(project, log.toFile(), 4434, "maya-sdk", StandardCharsets.UTF_8, 0, "Maya", true, null)
            try {
                val action = console.getOrCreateActions().getChildren(null).filterIsInstance<SelectLogInterpreterAction>().single()
                assertEquals("Set Maya as current interpreter", action.templatePresentation.text)
            } finally {
                Disposer.dispose(console)
            }
        } finally {
            Files.deleteIfExists(log)
        }
    }

    fun testActionTextIncludesMayaVersion() {
        val action = SelectLogInterpreterAction(project, "C:\\Autodesk\\Maya2025\\bin\\mayapy.exe")
        assertEquals("Set Maya 2025 as Current Interpreter", action.templatePresentation.text)
    }

    fun testInterpreterIconRespondsToThemeChanges() {
        IconLoader.activate()
        IconLoader.clearCache()
        try {
            val icon = SelectLogInterpreterAction(project, "Maya2025").templatePresentation.icon!!
            IconLoader.setUseDarkIcons(false)
            val light = renderIcon(icon)
            IconLoader.setUseDarkIcons(true)
            val dark = renderIcon(icon)
            IconLoader.setUseDarkIcons(false)
            val restoredLight = renderIcon(icon)

            assertTrue("Empty light icon", light.any { it ushr 24 != 0 })
            assertTrue("Empty dark icon", dark.any { it ushr 24 != 0 })
            assertFalse("Light and dark icons should differ", light.contentEquals(dark))
            assertTrue("Switching back should restore the light icon", light.contentEquals(restoredLight))
        } finally {
            IconLoader.setUseDarkIcons(!JBColor.isBright())
            IconLoader.deactivate()
        }
    }

    private fun renderIcon(icon: Icon): IntArray {
        val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            icon.paintIcon(null, graphics, 0, 0)
        } finally {
            graphics.dispose()
        }
        return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    }

    fun testSelectsRegisteredInterpreterForProjectWithoutOpenFile() {
        val directory = Files.createTempDirectory("maya-log-sdk")
        val executable = Files.createFile(directory.resolve("mayapy.exe"))
        val settings = ApplicationSettings.INSTANCE
        val previousMapping = settings.mayaSdkMapping.toMutableMap()
        val projectSettings = ProjectSettings.getInstance(project)
        val previousSelection = projectSettings.state.selectedSdk
        val previousProjectSdk = ProjectRootManager.getInstance(project).projectSdk
        val previousModuleSdk = ModuleRootManager.getInstance(module).sdk
        val wasSdkInherited = ModuleRootManager.getInstance(module).isSdkInherited
        val sdk = ProjectJdkTable.getInstance().createSdk("Maya log test", PythonSdkType.getInstance())
        try {
            ApplicationManager.getApplication().runWriteAction {
                sdk.sdkModificator.apply {
                    homePath = executable.toString()
                    commitChanges()
                }
                ProjectJdkTable.getInstance().addJdk(sdk)
            }
            val path = requireNotNull(sdk.homePath)
            settings.mayaSdkMapping[path] = ApplicationSettings.SdkInfo(path, 4434)

            val action = SelectLogInterpreterAction(project, path)
            val event = AnActionEvent.createEvent(action, DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
            action.actionPerformed(event)
            assertSame(sdk, ProjectRootManager.getInstance(project).projectSdk)
            assertSame(sdk, ModuleRootManager.getInstance(module).sdk)
            assertSame(sdk, PythonSdkUtil.findPythonSdk(module))
            assertEquals(path, projectSettings.selectedSdkName)
        } finally {
            ApplicationManager.getApplication().runWriteAction {
                ProjectRootManager.getInstance(project).projectSdk = previousProjectSdk
                if (wasSdkInherited) ModuleRootModificationUtil.setSdkInherited(module)
                else ModuleRootModificationUtil.setModuleSdk(module, previousModuleSdk)
                ProjectJdkTable.getInstance().removeJdk(sdk)
            }
            settings.mayaSdkMapping = previousMapping
            projectSettings.selectedSdkName = previousSelection
            Files.deleteIfExists(directory.resolve("mayapy.exe"))
            Files.deleteIfExists(directory)
        }
    }

    fun testMissingSdkDoesNotChangeProjectOrModule() {
        val settings = ApplicationSettings.INSTANCE
        val previousMapping = settings.mayaSdkMapping.toMutableMap()
        val currentSdk = ModuleRootManager.getInstance(module).sdk
        val currentProjectSdk = ProjectRootManager.getInstance(project).projectSdk
        val missingPath = "missing-mayapy"
        try {
            settings.mayaSdkMapping[missingPath] = ApplicationSettings.SdkInfo(missingPath, 4434)
            assertFalse(selectLogInterpreter(project, missingPath))
            assertSame(currentSdk, ModuleRootManager.getInstance(module).sdk)
            assertSame(currentProjectSdk, ProjectRootManager.getInstance(project).projectSdk)
        } finally {
            settings.mayaSdkMapping = previousMapping
        }
    }
}

class SelectLogInterpreterMultiModuleTest : HeavyPlatformTestCase() {
    fun testOpenFileInAnotherModuleDoesNotChangeTarget() {
        val directory = Files.createTempDirectory("maya-log-multi-module")
        val executable = Files.createFile(directory.resolve("mayapy.exe"))
        val otherExecutable = Files.createFile(directory.resolve("other-python.exe"))
        val settings = ApplicationSettings.INSTANCE
        val previousMapping = settings.mayaSdkMapping.toMutableMap()
        val projectSettings = ProjectSettings.getInstance(project)
        val previousSelection = projectSettings.state.selectedSdk
        val previousProjectSdk = ProjectRootManager.getInstance(project).projectSdk
        val previousModuleSdk = ModuleRootManager.getInstance(module).sdk
        val wasSdkInherited = ModuleRootManager.getInstance(module).isSdkInherited
        val sdk = ProjectJdkTable.getInstance().createSdk("Maya project test", PythonSdkType.getInstance())
        val otherSdk = ProjectJdkTable.getInstance().createSdk("Other module test", PythonSdkType.getInstance())
        val projectRoot = orCreateProjectBaseDir
        val hadProjectRoot = projectRoot in ModuleRootManager.getInstance(module).contentRoots
        var otherModule: Module? = null
        try {
            ApplicationManager.getApplication().runWriteAction {
                sdk.sdkModificator.apply {
                    homePath = executable.toString()
                    sdkAdditionalData = buildMayaSdkAdditionalData(directory)
                    commitChanges()
                }
                otherSdk.sdkModificator.apply {
                    homePath = otherExecutable.toString()
                    sdkAdditionalData = buildMayaSdkAdditionalData(directory)
                    commitChanges()
                }
                ProjectJdkTable.getInstance().addJdk(sdk)
                ProjectJdkTable.getInstance().addJdk(otherSdk)
                if (!hadProjectRoot) ModuleRootModificationUtil.addContentRoot(module, projectRoot)
                otherModule = ModuleManager.getInstance(project).newModule(directory.resolve("other.iml"), "JAVA_MODULE")
                ModuleRootModificationUtil.setModuleSdk(requireNotNull(otherModule), otherSdk)
            }
            val otherRoot = createChildDirectory(projectRoot, "other-module")
            val file = createChildData(otherRoot, "example.py")
            ApplicationManager.getApplication().runWriteAction {
                ModuleRootModificationUtil.addContentRoot(otherModule!!, otherRoot)
            }
            val editorManager = FileEditorManager.getInstance(project)
            PlatformTestUtil.waitForFuture(editorManager.requestOpenFile(file, FileEditorOpenRequest.defaults()))
            assertTrue(file in editorManager.selectedFiles)
            assertSame(module, PrimaryModuleManager.findPrimaryModule(project))
            assertSame(otherModule, ProjectFileIndex.getInstance(project).getModuleForFile(file))
            val path = requireNotNull(sdk.homePath)
            settings.mayaSdkMapping[path] = ApplicationSettings.SdkInfo(path, 4434)

            val action = SelectLogInterpreterAction(project, path)
            val event = AnActionEvent.createEvent(action, DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
            action.actionPerformed(event)

            assertSame(sdk, ProjectRootManager.getInstance(project).projectSdk)
            assertSame(sdk, ModuleRootManager.getInstance(module).sdk)
            assertSame(sdk, PythonSdkUtil.findPythonSdk(module))
            assertSame(otherSdk, ModuleRootManager.getInstance(otherModule!!).sdk)
            assertEquals(path, projectSettings.selectedSdkName)
        } finally {
            ApplicationManager.getApplication().runWriteAction {
                otherModule?.let { ModuleManager.getInstance(project).disposeModule(it) }
                if (!hadProjectRoot) {
                    ModuleRootModificationUtil.updateModel(module) { model ->
                        model.contentEntries.firstOrNull { it.file == projectRoot }?.let(model::removeContentEntry)
                    }
                }
                ProjectRootManager.getInstance(project).projectSdk = previousProjectSdk
                if (wasSdkInherited) ModuleRootModificationUtil.setSdkInherited(module)
                else ModuleRootModificationUtil.setModuleSdk(module, previousModuleSdk)
                ProjectJdkTable.getInstance().removeJdk(sdk)
                ProjectJdkTable.getInstance().removeJdk(otherSdk)
            }
            settings.mayaSdkMapping = previousMapping
            projectSettings.selectedSdkName = previousSelection
            Files.deleteIfExists(executable)
            Files.deleteIfExists(otherExecutable)
            Files.deleteIfExists(directory)
        }
    }
}
