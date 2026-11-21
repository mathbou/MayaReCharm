package debugattach

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.debugger.attach.PyAttachToProcessCommandLineState
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.sdk.PythonSdkType
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class MayaAttachToProcessDebugRunnerTest : BasePlatformTestCase() {
    fun testAttachHelperStartsAProcess() {
        val executable = Path.of(
            System.getProperty("java.home"), "bin", if (SystemInfo.isWindows) "java.exe" else "java"
        )
        val table = ProjectJdkTable.getInstance()
        val sdk = table.createSdk("attach-helper-test", PythonSdkType.getInstance())
        val source = Files.createTempFile("AttachHelperProbe", ".java")
        try {
            Files.writeString(
                source,
                """
                import java.net.Socket;
                class AttachHelperProbe {
                    public static void main(String[] args) throws Exception {
                        try (var socket = new Socket("127.0.0.1", Integer.parseInt(args[0]))) {
                            socket.getOutputStream().write(42);
                        }
                    }
                }
                """.trimIndent()
            )
            WriteAction.run<RuntimeException> {
                sdk.sdkModificator.apply {
                    homePath = executable.toString()
                    commitChanges()
                }
                table.addJdk(sdk)
            }
            val configuration = PythonConfigurationType.getInstance().factory
                .createTemplateConfiguration(project) as PythonRunConfiguration
            configuration.sdkHome = sdk.homePath
            configuration.isUseModuleSdk = false
            configuration.scriptName = source.toString()
            ServerSocket(0).use { server ->
                server.soTimeout = 30_000
                configuration.scriptParameters = server.localPort.toString()
                val environment = ExecutionEnvironmentBuilder
                    .create(project, DefaultDebugExecutor.getDebugExecutorInstance(), configuration).build()
                val state = MayaAttachToProcessCliState(configuration, environment)

                val result = state.startAttachHelper()
                try {
                    assertTrue(
                        "Attach helper must launch a real process",
                        result.processHandler is PyAttachToProcessCommandLineState.PyRemoteDebugProcessHandler
                    )
                    server.accept().use { assertEquals(42, it.getInputStream().read()) }
                } finally {
                    result.processHandler.destroyProcess()
                    UIUtil.dispatchAllInvocationEvents()
                    Disposer.dispose(result.executionConsole)
                }
            }
        } finally {
            WriteAction.run<RuntimeException> { table.removeJdk(sdk) }
            Files.deleteIfExists(source)
        }
    }

    fun testSavesDocumentsWithWriteAccess() {
        val file = myFixture.addFileToProject("attach-save-test.py", "before")
        val manager = FileDocumentManager.getInstance()
        val document = requireNotNull(manager.getDocument(file.virtualFile))
        WriteAction.run<RuntimeException> { document.setText("after") }
        assertTrue(manager.isDocumentUnsaved(document))

        val savedWithWriteAccess = AtomicBoolean()
        val app = ApplicationManager.getApplication()
        app.messageBus.connect(testRootDisposable).subscribe(
            FileDocumentManagerListener.TOPIC,
            object : FileDocumentManagerListener {
                override fun beforeAllDocumentsSaving() {
                    savedWithWriteAccess.set(app.isWriteAccessAllowed)
                }
            }
        )
        val result = CompletableFuture<Unit>()
        app.invokeLater({
            try {
                saveDocumentsBeforeAttach()
                result.complete(Unit)
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }, ModalityState.nonModal())
        PlatformTestUtil.waitForFuture(result)

        assertTrue("Saving must run inside a write action", savedWithWriteAccess.get())
        assertFalse(manager.isDocumentUnsaved(document))
        assertEquals("after", VfsUtilCore.loadText(file.virtualFile))
    }
}
