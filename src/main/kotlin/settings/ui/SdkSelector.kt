package settings.ui

import MayaBundle as Loc
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

class SdkSelector : JPanel(GridBagLayout()) {
    private val comboBox = ComboBox<String>()

    init {
        with(GridBagConstraints()) {
            insets = JBUI.insets(2)
            gridx = 0
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            add(JLabel(Loc.message("mayarecharm.ActiveMayaSdk")), this)

            gridx = 1
            weightx = 0.1
            add(comboBox, this)
        }
    }

    var selectedItem: String?
        get() = comboBox.selectedItem as String?
        set(value) {
            comboBox.selectedItem = value
        }

    var items: List<String>
        get() = List<String>(comboBox.itemCount) { comboBox.getItemAt(it) }
        set(value) {
            comboBox.removeAllItems()
            for (s in value) {
                comboBox.addItem(s)
            }
        }
}
