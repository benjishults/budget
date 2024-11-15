package bps.console.menu

import bps.console.SimpleConsoleIoTestFixture
import bps.console.app.MenuApplicationWithQuit
import bps.console.app.MenuSession
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
        "test selection" {
            val subject: ScrollingSelectionMenu<String> = ScrollingSelectionMenu(
                header = { null },
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
                outPrinter("You chose: '$selection'\n")
            }
            inputs.addAll(listOf("2", "4", "2", "7"))
            MenuApplicationWithQuit(subject, inputReader, outPrinter)
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                firstGroup,
                "Enter selection: ",
                "You chose: 'item 1'\n",
                firstGroup,
                "Enter selection: ",
                secondGroup,
                "Enter selection: ",
                "You chose: 'item 4'\n",
                secondGroup,
                "Enter selection: ",
                """
Quitting

""",
            )
        }
        "test with even division of items" {
            val subject: ScrollingSelectionMenu<String> = ScrollingSelectionMenu(
                header = { null },
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
                outPrinter("You chose: '$selection'\n")
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
                """
Quitting

""",
            )
        }
        "test with uneven division of items" {
            val subject: ScrollingSelectionMenu<String> = ScrollingSelectionMenu(
                header = { null },
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
                outPrinter("You chose: '$selection'\n")
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
                """
Quitting

""",
            )
        }
    }

}

val firstGroup = """
                     | 1. item 0
                     | 2. item 1
                     | 3. item 2
                     | 4. Next Items (n)
                     | 5. Back (b)
                     | 6. Quit (q)
                     |""".trimMargin()
val secondGroup = """
                     | 1. item 3
                     | 2. item 4
                     | 3. item 5
                     | 4. Next Items (n)
                     | 5. Previous Items (p)
                     | 6. Back (b)
                     | 7. Quit (q)
                     |""".trimMargin()
val thirdGroup = """
                     | 1. item 6
                     | 2. item 7
                     | 3. item 8
                     | 4. Next Items (n)
                     | 5. Previous Items (p)
                     | 6. Back (b)
                     | 7. Quit (q)
                     |""".trimMargin()
val lastGroup = """
                     | 1. item 9
                     | 2. Previous Items (p)
                     | 3. Back (b)
                     | 4. Quit (q)
                     |""".trimMargin()
val noItems = """
                     | 1. Previous Items (p)
                     | 2. Back (b)
                     | 3. Quit (q)
                     |""".trimMargin()
