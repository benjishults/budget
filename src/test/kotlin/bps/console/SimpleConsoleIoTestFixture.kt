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
 * This fixture allows the application to pause between tests.  This allows the JDBC connection to remain
 * open between tests.
 * The application will pause automatically
 * * after [inputs] is empty
 * * just prior to printing the next output
 * After re-populating the [inputs]
 * list, you can unpause the application by calling [unPause].
 *
 * After calling [unPause], the application will resume and run through the new inputs.
 * You will want to immediately call [waitForPause] so that the application will run through your inputs
 * prior to validating the results.
 */
interface ComplexConsoleIoTestFixture : SimpleConsoleIoTestFixture {

    val helper: Helper

    /** Call [waitForPause] before validation to allow the application to finish processing.  The application will
     * pause automatically when the [inputs] list is emptied.
     */
    fun waitForPause() = helper.waitForPause.get().await()

    fun unPause() = helper.unPause()

    override val outputs: MutableList<String>
        get() = helper.outputs
    override val inputs: MutableList<String>
        get() = helper.inputs
    override val inputReader: InputReader
        get() = helper.inputReader
    override val outPrinter: OutPrinter
        get() = helper.outPrinter

    class Helper {

        private val paused = AtomicBoolean(false)
        private val waitForUnPause = AtomicReference(CountDownLatch(0))

        // NOTE waitForPause before validation to allow the application to finish processing and get to the point of making
        //      more output so that validation happens after processing.
        val waitForPause = AtomicReference(CountDownLatch(1))

        private fun pause() {
            check(!paused.get()) { "already paused" }
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
