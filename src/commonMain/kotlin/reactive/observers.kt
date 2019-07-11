package reactive

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface PropertyGetEmitter {
    fun onGetValue(lifetime: Lifetime, handler: (property: ReadOnlyProperty<*, *>) -> Unit)
}

interface PropertyGetObserver: PropertyGetEmitter {
    fun emitGetValue(value: ReadOnlyProperty<*, *>)
}

interface PropertySetEmitter {
    fun onSetValue(lifetime: Lifetime, property: ReadOnlyProperty<*, *>, handler: () -> Unit)
}

interface PropertySetObserver: PropertySetEmitter {
    fun emitSetValue(property: ReadOnlyProperty<*, *>)
}

class PropertyGetObserverImpl: PropertyGetObserver {

    private val handlers = mutableListOf<(value: ReadOnlyProperty<*, *>) -> Unit>()

    override fun onGetValue(lifetime: Lifetime, handler: (value: ReadOnlyProperty<*, *>) -> Unit) {
        handlers.add(handler)
        lifetime.whenTerminated {
            handlers.remove(handler)
        }
    }


    override fun emitGetValue(value: ReadOnlyProperty<*, *>) {
        handlers.forEach {
            it(value)
        }
    }
}

class PropertySetObserverImpl: PropertySetObserver {

    private val handlersMap = mutableMapOf<ReadOnlyProperty<*, *>, MutableList<() -> Unit>>()

    override fun onSetValue(lifetime: Lifetime, property: ReadOnlyProperty<*, *>, handler: () -> Unit) {
        val handlers = handlersMap.getOrPut(property) { mutableListOf() }
        handlers.add(handler)
        lifetime.whenTerminated {
            handlers.remove(handler)
            if (handlers.isEmpty()) {
                handlersMap.remove(property)
            }
        }
    }
    override fun emitSetValue(property: ReadOnlyProperty<*, *>) {
        val handlers = handlersMap[property]
        handlers?.forEach {
            it()
        }
    }
}

class TransactionalPropertySetEmitter(private val observer: PropertySetObserver): PropertySetEmitter {
    private var transactionChanges = mutableSetOf<ReadOnlyProperty<*, *>>()
    private var handlersMap = mutableMapOf<ReadOnlyProperty<*, *>, MutableSet<() -> Unit>>()

    override fun onSetValue(lifetime: Lifetime, property: ReadOnlyProperty<*, *>, handler: () -> Unit) {
        val handlers = handlersMap.getOrPut(property) { mutableSetOf() }
        handlers.add(handler)
        lifetime.whenTerminated {
            handlers.remove(handler)
            if (handlers.isEmpty()) {
                handlersMap.remove(property)
            }
        }
        observer.onSetValue(lifetime, property) {
            transactionChanges.add(property)
        }
    }

    fun transaction(action: () -> Unit) {
        action()
        val handlers = mutableSetOf<() -> Unit>()
        transactionChanges.forEach { property ->
            handlersMap[property]?.forEach { handler ->
                handlers.add(handler)
            }
        }
        transactionChanges = mutableSetOf()
        handlers.forEach { it() }
    }
}

val globalGetObserver = PropertyGetObserverImpl()

val globalSetObserver = PropertySetObserverImpl()

val globalTransactionalSetEmitter = TransactionalPropertySetEmitter(globalSetObserver)

val globalSetEmitter: PropertySetEmitter = globalTransactionalSetEmitter


class GlobalObservable<T>(private var innerValue: T): ReadWriteProperty<Any?, T>, ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        globalGetObserver.emitGetValue(this)
        return innerValue
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (innerValue != value) {
            innerValue = value
            globalSetObserver.emitSetValue(this)
        }
    }
}

fun <T> observable(innerValue: T) = GlobalObservable(innerValue)

class CachedResult<T>(val value: T)

class GlobalComputed<T>(
    override val lifetime: Lifetime,
    private val expression: () -> T
): Lifetimed, ReadOnlyProperty<Any?, T> {
    var prevCachedResult: CachedResult<T>? = null
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        globalGetObserver.emitGetValue(this@GlobalComputed)
        val cachedResult = prevCachedResult
        if (cachedResult == null) {
            val (dependencies, result) = globalGetObserver.getExpressionDependencies(expression)
            dependencies.forEach {
                    globalSetEmitter.onSetValue(lifetime, it) {
                    if (prevCachedResult != null) {
                        prevCachedResult = null
                        globalSetObserver.emitSetValue(this@GlobalComputed)
                    }
                }
            }
            prevCachedResult = CachedResult(result)
            return result
        }
        return cachedResult.value
    }
}

fun <T>computed(lifetime: Lifetime, expression: () -> T): GlobalComputed<T> {
    return GlobalComputed(lifetime, expression)
}

fun <T>Lifetimed.computed(expression: () -> T): GlobalComputed<T> {
    return computed(lifetime, expression)
}
