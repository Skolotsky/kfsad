package view

import contrib.ringui.island.ringIsland
import contrib.ringui.island.ringIslandContent
import contrib.ringui.island.ringIslandHeader
import contrib.ringui.ringButton
import kotlinx.css.BorderStyle
import kotlinx.css.Color
import kotlinx.css.properties.borderBottom
import kotlinx.css.px
import model.PostWithComments
import model.User
import react.*
import reactive.action
import reactive.observable
import styled.StyleSheet
import styled.css
import styled.styledDiv

object PostStyles : StyleSheet("PostStyles", isStatic = true) {
    val noComments by css {
        marginBottom = 8.px
    }

    val body by css {
        +noComments

        paddingBottom = 8.px
        borderBottom(1.px, BorderStyle.solid, Color("#000").withAlpha(0.1))
    }

    val comment by css {
        +body

        lastOfType {
            borderBottomStyle = BorderStyle.none
        }
    }
}

interface PostProps : RProps {
    var postWithComments: PostWithComments
    var user: User?
    var onMoreComments: () -> Unit
}

class PostView : ReactiveComponent<PostProps>() {
    private val post
        get() = props.postWithComments.post

    private val comments
        get() = props.postWithComments.comments

    var noMore: Boolean by observable(false)
    var loading: Boolean by observable(false)

    override fun componentDidUpdate(prevProps: PostProps) {
        if (loading && prevProps != props) {
            action {
                noMore = prevProps.postWithComments.comments.size == props.postWithComments.comments.size
                loading = false
            }
        }
    }

    override fun RBuilder.render() {
        ringIsland {
            ringIslandHeader {
                attrs {
                    border = true
                }
                +post.title
            }

            ringIslandContent {
                props.user?.let {
                    userView(it) {
                        css {
                            marginBottom = 16.px
                        }
                    }
                }

                styledDiv {
                    css {
                        if (comments.isNotEmpty()) {
                            +PostStyles.body
                        } else {
                            +PostStyles.noComments
                        }
                    }
                    +post.body
                }

                comments.forEach {
                    commentView(it) {
                        css {
                            +PostStyles.comment
                        }
                    }
                }

                if (!noMore) {
                    ringButton {
                        attrs {
                            loader = loading
                            onMouseDown = {
                                setState {
                                    loading = true
                                }
                                props.onMoreComments()
                            }
                        }

                        +"Load more comments"
                    }
                }
            }
        }
    }
}

fun RBuilder.postView(
    post: PostWithComments,
    user: User? = null,
    onMoreComments: () -> Unit,
    handler: RHandler<PostProps> = {}
) {
    child(PostView::class) {
        attrs.postWithComments = post
        attrs.user = user
        attrs.onMoreComments = onMoreComments
        handler()
    }
}