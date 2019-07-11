package reactive

interface Lifetime: Lifetimed {
    val isTerminated: Boolean

    override val lifetime
        get() = this

    fun onTerminate(handler: () -> Unit) {}
    fun whenTerminated(handler: () -> Unit) {}
}

open class TerminatableLifetime: Lifetime {
    private val handlers = mutableListOf<() -> Unit>()
    final override var isTerminated = false
        private set

    fun terminate() {
        if (!isTerminated) {
            handlers.forEach {
                it()
            }
            isTerminated = false
        }
    }

    override fun onTerminate(handler: () -> Unit) {
        handlers.add(handler)
    }

    override fun whenTerminated(handler: () -> Unit) {
        handlers.add(handler)
    }
}

fun Lifetime.nested(): TerminatableLifetime {
    val lifetime = TerminatableLifetime()
    whenTerminated { lifetime.terminate() }
    return lifetime
}

fun <T>lifetimed(handler: Lifetimed.() -> T): T {
    val lifetime = TerminatableLifetime()
    val result = lifetime.handler()
    lifetime.terminate()
    return result
}

interface Lifetimed {
    val lifetime: Lifetime
}

class SequentialLifetimes(override val lifetime: Lifetime): Lifetimed {
    private var currentLifetime: TerminatableLifetime?= null
    fun next(): Lifetime {
        val lastLifetime = currentLifetime
        if (lastLifetime != null) {
            lastLifetime.terminate()
        }
        val nextLifetime = lifetime.nested()
        currentLifetime = nextLifetime
        return nextLifetime
    }
}