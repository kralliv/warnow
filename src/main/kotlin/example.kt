import warnow.NestedContext
import warnow.mutate
import warnow.within
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class ExamplePanel(context: NestedContext) : JPanel() {

    private val names: List<String> by warnow.state.names within context
    private var showIds: Boolean by warnow.state.display.show_ids

    private val button: JButton
    private val resetButton: JButton

    init {
        layout = BorderLayout()

        button = JButton()
        button.text = "Show Ids"
        button.addActionListener {
            showIds = !showIds
        }
        add(button)

        resetButton = JButton()
        resetButton.text = "Reset"
        resetButton.addActionListener {
            var name: String? by warnow.state.name within context.parent

            mutate(context.parent) {
                name = names.firstOrNull()
                showIds = false
            }
        }
        add(resetButton, BorderLayout.EAST)
    }
}

