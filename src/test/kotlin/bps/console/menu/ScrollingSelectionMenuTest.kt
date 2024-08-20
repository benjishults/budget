package bps.console.menu

import bps.console.ComplexConsoleIoTestFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.concurrent.thread

class ScrollingSelectionMenuTest : FreeSpec(),
    ComplexConsoleIoTestFixture by ComplexConsoleIoTestFixture() {

    init {
        System.setProperty("kotest.assertions.collection.print.size", "1000")
        System.setProperty("kotest.assertions.collection.enumerate.size", "1000")
        val subject: ScrollingSelectionMenu<String> = ScrollingSelectionMenu(
            header = null,
            itemListGenerator = { limit, offset ->
                buildList {
                    repeat(limit) {
                        if (it >= 0 && it + offset < 100)
                            add("item ${it + offset}")
                    }
                }
            },
        ) { _: MenuSession, selection: String ->
            outPrinter("You chose: '$selection'")
        }
        val application = MenuApplicationWithQuit(subject, inputReader, outPrinter)
        thread {
            application.run()
        }
        "asdf" {
            inputs.addAll(listOf("31", "31", "31", "11", "1", "12", "32", "31", "32", "32", "32", "31", "34"))
            unPause()
            waitForPause()
            outputs shouldContainExactly listOf(
                first30,
                "Enter selection: ",
                secondThirty,
                "Enter selection: ",
                thirdThirty,
                "Enter selection: ",
                last10,
                "Enter selection: ",
                noItems,
                "Enter selection: ",
                last10,
                "Enter selection: ",
                thirdThirty,
                "Enter selection: ",
                secondThirty,
                "Enter selection: ",
                thirdThirty,
                "Enter selection: ",
                secondThirty,
                "Enter selection: ",
                first30,
                "Enter selection: ",
                first30,
                "Enter selection: ",
                secondThirty,
                "Enter selection: ",
            )
        }
    }

}

val first30 = """
                     | 1. item 0
                     | 2. item 1
                     | 3. item 2
                     | 4. item 3
                     | 5. item 4
                     | 6. item 5
                     | 7. item 6
                     | 8. item 7
                     | 9. item 8
                     |10. item 9
                     |11. item 10
                     |12. item 11
                     |13. item 12
                     |14. item 13
                     |15. item 14
                     |16. item 15
                     |17. item 16
                     |18. item 17
                     |19. item 18
                     |20. item 19
                     |21. item 20
                     |22. item 21
                     |23. item 22
                     |24. item 23
                     |25. item 24
                     |26. item 25
                     |27. item 26
                     |28. item 27
                     |29. item 28
                     |30. item 29
                     |31. Next Items
                     |32. Back
                     |33. Quit
                     |""".trimMargin()
val secondThirty = """
                | 1. item 30
                | 2. item 31
                | 3. item 32
                | 4. item 33
                | 5. item 34
                | 6. item 35
                | 7. item 36
                | 8. item 37
                | 9. item 38
                |10. item 39
                |11. item 40
                |12. item 41
                |13. item 42
                |14. item 43
                |15. item 44
                |16. item 45
                |17. item 46
                |18. item 47
                |19. item 48
                |20. item 49
                |21. item 50
                |22. item 51
                |23. item 52
                |24. item 53
                |25. item 54
                |26. item 55
                |27. item 56
                |28. item 57
                |29. item 58
                |30. item 59
                |31. Next Items
                |32. Previous Items
                |33. Back
                |34. Quit
                |""".trimMargin()
val thirdThirty = """
                | 1. item 60
                | 2. item 61
                | 3. item 62
                | 4. item 63
                | 5. item 64
                | 6. item 65
                | 7. item 66
                | 8. item 67
                | 9. item 68
                |10. item 69
                |11. item 70
                |12. item 71
                |13. item 72
                |14. item 73
                |15. item 74
                |16. item 75
                |17. item 76
                |18. item 77
                |19. item 78
                |20. item 79
                |21. item 80
                |22. item 81
                |23. item 82
                |24. item 83
                |25. item 84
                |26. item 85
                |27. item 86
                |28. item 87
                |29. item 88
                |30. item 89
                |31. Next Items
                |32. Previous Items
                |33. Back
                |34. Quit
                |""".trimMargin()
val last10 = """
                | 1. item 90
                | 2. item 91
                | 3. item 92
                | 4. item 93
                | 5. item 94
                | 6. item 95
                | 7. item 96
                | 8. item 97
                | 9. item 98
                |10. item 99
                |11. Next Items
                |12. Previous Items
                |13. Back
                |14. Quit
                |""".trimMargin()
val noItems = """
                     | 1. Previous Items
                     | 2. Back
                     | 3. Quit
                     |""".trimMargin()
