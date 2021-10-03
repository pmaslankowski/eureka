package pmaslankowski.kotlincoroutines

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.*
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

class CoroutinesExamples {

    @Nested
    class `simple coroutine without suspension` {

        val tracker = Tracker()

        @Test
        fun `starts using startCoroutine ext method`() {
            // when
            ::foo.startCoroutine(
                DefaultContinuation {
                    tracker.emitMessage("Continuation")
                }
            )

            // then
            tracker.assertEmitted(
                "Foo - start",
                "Bar",
                "Foo - end",
                "Continuation"
            )
        }

        suspend fun foo() {
            tracker.emitMessage("Foo - start")
            bar()
            tracker.emitMessage("Foo - end")
        }

        suspend fun bar() {
            tracker.emitMessage("Bar")
        }
    }

    @Nested
    class `coroutine with suspension using suspendCoroutineUninterceptedOrReturn` {

        val tracker = Tracker()
        var suspendingBarContinuation: Continuation<Unit>? = null

        @Test
        fun `starts and resumes from top level function`() {
            // when
            ::suspendingFoo.startCoroutine(DefaultContinuation())
            tracker.emitMessage("toplevel - returned to the first non suspending function in a stack frame")
            suspendingBarContinuation?.resumeWith(Result.success(Unit))

            // then
            tracker.assertEmitted(
                "Foo - start",
                "Bar - before suspension",
                "Bar - just before suspension, after continuation has been saved",
                "toplevel - returned to the first non suspending function in a stack frame",
                "Bar - after suspension (resumed)",
                "Foo - end (resumed)"
            )
        }

        suspend fun suspendingFoo() {
            tracker.emitMessage("Foo - start")
            suspendingBar()
            tracker.emitMessage("Foo - end (resumed)")
        }

        suspend fun suspendingBar() {
            tracker.emitMessage("Bar - before suspension")
            suspendCoroutineUninterceptedOrReturn { cont: Continuation<Unit> ->
                suspendingBarContinuation = cont
                tracker.emitMessage("Bar - just before suspension, after continuation has been saved")
                COROUTINE_SUSPENDED
            }
            tracker.emitMessage("Bar - after suspension (resumed)")
        }
    }

    // TODO: check if it's possible to rerun unintercepted continuation - it should be possible as it's not wrapped in
    // SafeContinuation

    class DefaultContinuation<T>(
        override val context: CoroutineContext = EmptyCoroutineContext,
        val resumeWithCallback: (Result<T>) -> Unit = {}
    ) : Continuation<T> {

        override fun resumeWith(result: Result<T>) {
            resumeWithCallback(result)
        }
    }

    class Tracker {
        private val messages = mutableListOf<String>()

        fun emitMessage(message: String) = messages.add(message)

        fun assertEmitted(vararg expectedMessages: String) {
            if (messages.size != expectedMessages.size) {
                throw AssertionError(
                    "Expected ${expectedMessages.size} messages, but emitted ${messages.size} messages." +
                        "\nExpected: ${expectedMessages.toList()}\n  Actual: $messages"
                )
            }
            for (i in messages.indices) {
                if (messages[i] != expectedMessages[i]) {
                    throw AssertionError("Expected ${expectedMessages[i]} but received ${messages[i]}")
                }
            }
        }
    }
}
