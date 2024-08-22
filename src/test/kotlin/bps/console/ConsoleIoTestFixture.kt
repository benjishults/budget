package bps.console

import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.core.spec.Spec
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Usage:
 *
 * 1. Call [clearInputsAndOutputsBeforeEach] to make sure the [inputs] and [outputs] are cleared between tests
 * 2. Plug the [inputReader] and [outPrinter] into your menus and application
 * 3. Populate the [inputs] list with the [String]s you want to use as inputs
 * 4. At the end of each test, validate that the [outputs] contains the outputs you expected
 *
 * Example:
 *
 * ```kotlin
 * class ScrollingSelectionMenuTest : FreeSpec(),
 *     SimpleConsoleIoTestFixture {
 *
 *     override val inputs: MutableList<String> = mutableListOf()
 *     override val outputs: MutableList<String> = mutableListOf()
 *
 *     init {
 *         clearInputsAndOutputsBeforeEach()
 *         "test ..." {
 *
 *             // set up application or menus omitted
 *
 *             inputs.addAll(listOf("2", "4", "2", "7"))
 *             MenuApplicationWithQuit(subject, inputReader, outPrinter)
 *                 .use {
 *                     it.run()
 *                 }
 *             outputs shouldContainExactly listOf(/* ... */)
 *         }
 *     }
 * }
 * ```
 */
interface SimpleConsoleIoTestFixture {

    val inputs: MutableList<String>
    val inputReader
        get() = InputReader {
            inputs.removeFirst()
        }

    val outputs: MutableList<String>
    val outPrinter: OutPrinter
        get() = OutPrinter {
            outputs.add(it)
        }

    fun Spec.clearInputsAndOutputsBeforeEach() {
        beforeEach {
            inputs.clear()
            outputs.clear()
        }
    }

}

/**
 * This fixture allows the application to pause between tests (allowing the JDBC connection to remain
 * open between tests).
 * The application will pause automatically
 * * when [inputs] is empty
 * * just prior to printing the next output
 * After re-populating the [inputs]
 * list, you can unpause the application by calling [unPause].
 *
 * After calling [unPause], the application will resume and run through the new inputs.
 * You will want to immediately call [waitForPause] so that the application will run through your inputs
 * prior to validating the results.
 *
 * Usage:
 *
 * ```kotlin
 *            // beginning of each test looks something like this:
 *            // populate list of inputs for test
 *            inputs.addAll(
 *                listOf("2", "3", "300", "", "5", "100", "", "10"),
 *            )
 *            // unpause to allow the application to run through these inputs
 *            unPause()
 *            // wait for the application to run through those inputs before beginning validation of results
 *            waitForPause()
 *            // validations...
 *            outputs shouldContainExactly listOf( /* ... */ )
 *
 * ```
 *
 * If you want to capture the Quitting output and ensure that the application thread ends before you go on to the
 * next test, then you might ought to be using the [SimpleConsoleIoTestFixture] instead.  However, if you need to
 * use this in that way for some reason, here's how:
 *
 * ```kotlin
 *             val applicationThread = thread(name = "Application Thread 1") {
 *                 application
 *                     .use {
 *                         it.run()
 *                     }
 *             }
 *             inputs.addAll(listOf("4", "4", "4", "1", "5", "5", "4", "5", "5", "4", "7"))
 *             unPause()
 *             waitForPause()
 *             // unpause so that the process can end by printing the Quit message
 *             unPause()
 *             // make sure this thread dies so as not to interfere with later tests
 *             applicationThread.join(Duration.ofMillis(20)).shouldBeTrue()
 *             outputs shouldContainExactly listOf( /* ...*/ )
 * ```
 */
interface ComplexConsoleIoTestFixture : SimpleConsoleIoTestFixture {

    val helper: Helper

    /** Call [waitForPause] before validation to allow the application to finish processing.  The application will
     * pause automatically when the [inputs] list is emptied.
     */
    fun waitForPause() =
        helper
            .waitForPause
            .get()
            .await()

    fun unPause() =
        helper.unPause()

    override val outputs: MutableList<String>
        get() = helper.outputs
    override val inputs: MutableList<String>
        get() = helper.inputs
    override val inputReader: InputReader
        get() = helper.inputReader
    override val outPrinter: OutPrinter
        get() = helper.outPrinter

    companion object {
        operator fun invoke(): ComplexConsoleIoTestFixture {
            return object : ComplexConsoleIoTestFixture {
                override val helper: Helper = Helper()
            }
        }
    }

    class Helper {

        private val paused = AtomicBoolean(true)
        private val waitForUnPause = AtomicReference(CountDownLatch(0))

        // NOTE waitForPause before validation to allow the application to finish processing and get to the point of making
        //      more output so that validation happens after processing.
        val waitForPause = AtomicReference(CountDownLatch(1))

        private fun pause() {
            waitForUnPause.set(CountDownLatch(1))
            paused.set(true)
            waitForPause.get().countDown()
        }

        fun unPause() {
            check(paused.get()) { "not paused" }
            waitForPause.set(CountDownLatch(1))
            paused.set(false)
            waitForUnPause.get().countDown()
        }

        // NOTE the thread clearing this is not the thread that adds to it
        val inputs = CopyOnWriteArrayList<String>()
        val inputReader = InputReader {
            inputs.removeFirst()
        }

        // NOTE the thread clearing this is not the thread that adds to it
        val outputs = CopyOnWriteArrayList<String>()

        // NOTE when the inputs is empty, the application will pause itself
        val outPrinter = OutPrinter {
            if (inputs.isEmpty()) {
                pause()
            }
            if (paused.get())
                waitForUnPause.get().await()
            outputs.add(it)
        }

    }

}
