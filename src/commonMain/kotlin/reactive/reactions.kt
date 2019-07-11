package reactive

import kotlin.properties.ReadOnlyProperty

interface ReactionDependency {
    fun onChange(lifetime: Lifetime, handler: () -> Unit)
}

interface ReactionOptions {
    val initialReaction: Boolean
    val once: Boolean
}

val defaultReactionOptions = object : ReactionOptions {
    override val initialReaction = false
    override val once = false
}

val onceReactionOptions = object : ReactionOptions {
    override val initialReaction = false
    override val once = true
}

fun Lifetimed.reaction(
    dependency: ReactionDependency,
    options: ReactionOptions = defaultReactionOptions,
    handler: () -> Unit
) {
    if (options.initialReaction) {
        handler()
    }
    val nestedLifetime = lifetime.nested()
    dependency.onChange(nestedLifetime) {
        handler()
        if (options.once) {
            nestedLifetime.terminate()
        }
    }
}

class ReactionDependencyContainer(
    private val observer: PropertySetEmitter,
    private val dependencies: Set<ReadOnlyProperty<*, *>>
): ReactionDependency {
    override fun onChange(lifetime: Lifetime, handler: () -> Unit) {
        dependencies.forEach {
            observer.onSetValue(lifetime, it, handler)
        }
    }
}

fun <T>PropertyGetEmitter.getExpressionDependencies(
    expression: () -> T
): Pair<Set<ReadOnlyProperty<*, *>>, T> {
    val dependencies = mutableSetOf<ReadOnlyProperty<*, *>>()
    val result = lifetimed {
        onGetValue(lifetime) {
            dependencies.add(it)
        }
        expression()
    }
    return dependencies to result
}

fun Lifetimed.reaction(
    expression: () -> Unit,
    setEmitter: PropertySetEmitter = globalSetEmitter,
    getEmitter: PropertyGetEmitter = globalGetObserver,
    options: ReactionOptions = defaultReactionOptions,
    handler: () -> Unit
) {
    val (dependencies) = getEmitter.getExpressionDependencies(expression)
    val dependency = ReactionDependencyContainer(setEmitter, dependencies)
    reaction(dependency, options, handler)
}

fun Lifetimed.once(
    expression: () -> Unit,
    setEmitter: PropertySetEmitter = globalSetEmitter,
    getEmitter: PropertyGetEmitter = globalGetObserver,
    options: ReactionOptions = onceReactionOptions,
    handler: () -> Unit
) {
    reaction(expression, setEmitter, getEmitter, options, handler)
}


fun Lifetimed.autorun(
    setEmitter: PropertySetEmitter = globalSetEmitter,
    getEmitter: PropertyGetEmitter = globalGetObserver,
    handler: () -> Unit
) {
    once(handler, setEmitter, getEmitter) {
        autorun(setEmitter, getEmitter ,handler)
    }
}

fun action(
    transactionalSetEmitter: TransactionalPropertySetEmitter = globalTransactionalSetEmitter,
    handler: () -> Unit
) {
    transactionalSetEmitter.transaction(handler)
}
