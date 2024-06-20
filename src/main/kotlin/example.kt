import warnow.NestedContext
import warnow.mutate
import warnow.within
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class Example(context: Context) {

    val flag by expect { flags.flag within context }
    var text by define { text as String? initially null }

    init {
        // property access syntax
        expect { some.nested.property }

        // property definition syntax
        define { some.nested.property as List<Int> initially null }

        // value access syntax
        access { authentication.username }

        // value update syntax
        mutate {
            username = "kralli"
        }

        ::text.addInvalidationListener {}

        define { ui.description as String initially "A Hello World Program" within context }
    }
}

fun some(): Boolean {
    return false
}

fun main() {
    val context = createContextImplementation()
    define { ui.message as String initially "Hello World" }

    expect { ui.message }
        .addInvalidationListener { text ->
            print("context => ")
            println(text)
        }

    mutate {
        if (some()) {
            return@mutate
        }

        ui(context) {
            message = "Hello World 2"
            dong
        }
    }

    access {
        ui(context) {
            message
        }
    }

    println("a")

    Example(context)
}

fun none() {
    define { ui.some as String initially "hallo" }
}

//some experimentation
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
