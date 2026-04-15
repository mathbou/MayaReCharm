package settings

import settings.ui.SdkTablePanel

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener

class MayaSdkConfigurable(project: Project) : SearchableConfigurable, Configurable.NoScroll {
    companion object {
        const val ID = "settings.MayaSdkConfigurable"
    }

    private val settings = ApplicationSettings.INSTANCE

    private val myPanel = JPanel(GridBagLayout()).also {
        it.addAncestorListener(object : AncestorListener {
            override fun ancestorAdded(event: AncestorEvent?) {
                ApplicationSettings.INSTANCE.refreshPythonSdks()
                reset()
            }

            override fun ancestorMoved(event: AncestorEvent?) {}

            override fun ancestorRemoved(event: AncestorEvent?) {}
        })
    }

    private val mySdkPanel = SdkTablePanel(project).also {
        it.changed += {
            ApplicationSettings.INSTANCE.refreshPythonSdks()
        }
    }

    init {
        with(GridBagConstraints()) {
            insets = Insets(2, 2, 2, 2)
            weightx = 1.0
            gridx = 0
            gridy = 0

            fill = GridBagConstraints.HORIZONTAL

            insets = Insets(2, 2, 0, 2)
            gridy = 1
            weighty = 1.0
            gridheight = GridBagConstraints.RELATIVE
            fill = GridBagConstraints.BOTH
            myPanel.add(mySdkPanel, this)
        }
    }

    override fun getId(): String {
        return ID
    }

    override fun getDisplayName(): String {
        return "MayaReCharm"
    }

    override fun getHelpTopic(): String? {
        return null // TODO
    }

    override fun createComponent(): JComponent {
        return myPanel
    }

    override fun isModified(): Boolean {
        val entries = settings.mayaSdkMapping.values.toSet() != mySdkPanel.data.toSet()
        return entries
    }

    override fun reset() {
        mySdkPanel.data.clear()
        mySdkPanel.data.addAll(settings.mayaSdkMapping.values.sortedBy { it.mayaPyPath })
    }

    override fun apply() {
        settings.mayaSdkMapping = mySdkPanel.data.map { it.mayaPyPath to it }.toMap().toMutableMap()
    }
}
