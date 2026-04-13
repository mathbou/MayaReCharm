package run

import MayaBundle as Loc
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class MayaReCharmConfigType : ConfigurationType {
    override fun getDisplayName(): String {
        return "MayaReCharm"
    }

    override fun getConfigurationTypeDescription(): String {
        return Loc.message("mayarecharm.runconfig.ConfigType")
    }

    override fun getIcon(): Icon {
        return IconLoader.getIcon("/icons/MayaReCharm_ToolWindow.png", this::class.java)
    }

    override fun getId(): String {
        return "MAYARECHARM_RUN_CONFIGURATION"
    }

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(MayaReCharmConfigFactory(this))
    }
}

class MayaReCharmConfigFactory(type: MayaReCharmConfigType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return MayaReCharmRunConfiguration(project, this, "MayaReCharm")
    }

    override fun getName(): String {
        return Loc.message("mayarecharm.runconfig.ConfigFactory")
    }

    override fun getId(): String {
        return "MAYARECHARM_RUN_CONFIGURATION"
    }
}
