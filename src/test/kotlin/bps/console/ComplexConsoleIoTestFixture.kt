package bps.console

import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * This fixture allows the application to pause between tests (allowing the JDBC connection to remain
 * open between tests).  Using the [validateInteraction] function will work for most use cases.
 *
 * Usage:
 *
 * ```kotlin
 *                 validateInteraction(
 *                     expectedOutputs = listOf("Enter the amount of income: "),
 *                     toInput = listOf("5000"),
 *                 )
 *                 validateInteraction(
 *                     expectedOutputs = listOf(
 *                         "Enter description of income [income into $defaultCheckingAccountName]: ",
 *                         "Use current time [Y]? ",
 *                         """
 *                         |Select account receiving the income:
 *                         | 1.   5,000.00 | Checking
 *                         | 2.       0.00 | Wallet
 *                         | 3. Back (b)
 *                         | 4. Quit (q)
 *                         |""".trimMargin(),
 *                         "Enter selection: ",
 *                     ),
 *                     toInput = listOf("", "", "2"),
 *                 )
 * ```
 * ## Expert Mode
 *
 * The application will pause automatically
 * * when [inputs] is empty
 * * just prior to printing the next output
 *
 * After re-populating the [inputs] list, you can unpause the application by calling [unPause].
 *
 * After calling [unPause], the application will resume and run through the new inputs.
 * You will want to immediately call [waitForPause] so that the application will run through your inputs
 * prior to validating the results.
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
    fun waitForPause(milliSeconds: Long): Boolean =
        helper
            .waitForPause
            .get()
            .await(helper.awaitMillis, TimeUnit.MILLISECONDS)

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
        operator fun invoke(debugging: Boolean = false): ComplexConsoleIoTestFixture {
            return object : ComplexConsoleIoTestFixture {
                override val helper: Helper =
                    Helper(
                        if (debugging)
                            MAX_LONG_MILLIS_FOR_WAITING_FOR_PAUSE_DURING_DEBUGGING
                        else
                            ONE_SECOND_IN_MILLIS_FOR_WAITING_FOR_PAUSE,
                    )
            }
        }
    }

    class Helper(val awaitMillis: Long) {

        private val paused = AtomicBoolean(false)
        private val waitForUnPause = AtomicReference(CountDownLatch(0))

        // NOTE waitForPause before validation to allow the application to finish processing and get to the point of making
        //      more output so that validation happens after processing.
        val waitForPause = AtomicReference(CountDownLatch(1))

        private fun pause() {
//            check(!paused.get()) { "Already paused" }
            waitForUnPause.set(CountDownLatch(1))
            paused.set(true)
            waitForPause.get().countDown()
        }

        fun unPause() {
//            check(paused.get()) { "Not paused" }
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
                waitForUnPause.get()
                    .await(awaitMillis, TimeUnit.MILLISECONDS)
                    .shouldBeTrue()
            outputs.add(it)
        }

    }

    fun validateInteraction(expectedOutputs: List<String>, toInput: List<String>) {
        outputs.clear()
        inputs.addAll(toInput)
        unPause()
        waitForPause(helper.awaitMillis).shouldBeTrue()
        outputs shouldContainExactly expectedOutputs
    }

}
