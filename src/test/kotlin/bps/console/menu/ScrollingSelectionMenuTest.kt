package bps.console.menu

import bps.console.SimpleConsoleIoTestFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly

class ScrollingSelectionMenuTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val inputs: MutableList<String> = mutableListOf()
    override val outputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        System.setProperty("kotest.assertions.collection.print.size", "1000")
        System.setProperty("kotest.assertions.collection.enumerate.size", "1000")
        // TODO write a test where an empty list is possible
        // TODO write a test where something is selected
        "test with even division of items" {
            val subject: ScrollingSelectionMenu<String> = ScrollingSelectionMenu(
                header = null,
                limit = 3,
                itemListGenerator = { limit, offset ->
                    buildList {
                        repeat(limit) {
                            if (it + offset < 9)
                                add("item ${it + offset}")
                        }
                    }
                },
            ) { _: MenuSession, selection: String ->
                outPrinter("You chose: '$selection'")
            }
            inputs.addAll(listOf("4", "4", "4", "1", "5", "5", "4", "5", "5", "4", "7"))
            MenuApplicationWithQuit(subject, inputReader, outPrinter)
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                firstGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                thirdGroup,
                "Enter selection: ",
                noItems,
                "Enter selection: ",
                thirdGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                firstGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                firstGroup,
                "Enter selection: ",
                firstGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                "Quitting\n",
            )
        }
        "test with uneven division of items" {
            val subject: ScrollingSelectionMenu<String> = ScrollingSelectionMenu(
                header = null,
                limit = 3,
                itemListGenerator = { limit, offset ->
                    buildList {
                        repeat(limit) {
                            if (it + offset < 10)
                                add("item ${it + offset}")
                        }
                    }
                },
            ) { _: MenuSession, selection: String ->
                outPrinter("You chose: '$selection'")
            }
            inputs.addAll(listOf("4", "4", "4", "2", "5", "5", "4", "5", "6"))
            MenuApplicationWithQuit(subject, inputReader, outPrinter)
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                firstGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                thirdGroup,
                "Enter selection: ",
                lastGroup,
                "Enter selection: ",
                thirdGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                firstGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                firstGroup,
                "Enter selection: ",
                "Quitting\n",
            )
        }
    }

}

val firstGroup = """
                     | 1. item 0
                     | 2. item 1
                     | 3. item 2
                     | 4. Next Items
                     | 5. Back
                     | 6. Quit
                     |""".trimMargin()
val secondGroup = """
                     | 1. item 3
                     | 2. item 4
                     | 3. item 5
                     | 4. Next Items
                     | 5. Previous Items
                     | 6. Back
                     | 7. Quit
                     |""".trimMargin()
val thirdGroup = """
                     | 1. item 6
                     | 2. item 7
                     | 3. item 8
                     | 4. Next Items
                     | 5. Previous Items
                     | 6. Back
                     | 7. Quit
                     |""".trimMargin()
val lastGroup = """
                     | 1. item 9
                     | 2. Previous Items
                     | 3. Back
                     | 4. Quit
                     |""".trimMargin()
val noItems = """
                     | 1. Previous Items
                     | 2. Back
                     | 3. Quit
                     |""".trimMargin()
