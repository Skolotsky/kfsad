package view

import react.*
import reactive.*

class ReactiveComponentState(val counter: Int) : RState

abstract class ReactiveComponent<Props: RProps> : RComponent<Props, ReactiveComponentState>(), Lifetimed {
    final override val lifetime = TerminatableLifetime()
    private val renderLifetimes = SequentialLifetimes(lifetime)

    init {
        state = ReactiveComponentState(0)
    }

    open fun Lifetimed.componentWillUnmount() {}

    final override fun componentWillUnmount() {
        console.log("component unmounted")
        lifetime.componentWillUnmount()
        lifetime.terminate()
    }

    open fun componentDidUpdate(prevProps: Props) {

    }

    final override fun componentDidUpdate(prevProps: Props, prevState: ReactiveComponentState, snapshot: Any) {
        componentDidUpdate(prevProps)
    }

    final override fun render(): dynamic {
        var result: dynamic = null
        renderLifetimes.next().once({
            result = super.render()
        }) {
            setState(ReactiveComponentState(state.counter + 1))
        }
        return result
    }
}
