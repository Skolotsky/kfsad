package view

import contrib.ringui.header.ringHeader
import contrib.ringui.header.ringLogo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.css.padding
import kotlinx.css.px
import model.PostWithComments
import model.User
import react.*
import reactive.action
import reactive.observable
import services.CommentsService
import services.PostWithCommentsService
import services.UserService
import styled.StyleSheet
import styled.css
import styled.styledA
import styled.styledDiv
import kotlin.random.Random

val jetbrainsLogo = kotlinext.js.require("@jetbrains/logos/jetbrains/jetbrains-simple.svg")

private object ApplicationStyles : StyleSheet("ApplicationStyles", isStatic = true) {
    val wrapper by css {
        padding(32.px, 16.px)
    }

    val post by css {
        marginBottom = 32.px
    }
}

interface ApplicationProps : RProps {
    var coroutineScope: CoroutineScope
}

class ApplicationComponent : ReactiveComponent<ApplicationProps>() {
    var postWithComments: List<PostWithComments> by observable(emptyList())
    var users: Map<Int, User> by observable(emptyMap())

    private val coroutineContext
        get() = props.coroutineScope.coroutineContext

    override fun componentDidMount() {
        val postWithCommentsService = PostWithCommentsService(coroutineContext)
        val userService = UserService(coroutineContext)

        props.coroutineScope.launch {
            val loadedPostsWithComments = postWithCommentsService.getPostsWithComments()

            action {
                console.log("postWithComments += loadedPostsWithComments")
                postWithComments += loadedPostsWithComments
            }

            // Parallel coroutines execution example
            val userIds = loadedPostsWithComments.map { it.post.userId }.toSet()
            val loadedUsers = userIds
                .map { async { userService.getUser(it) } }
                .awaitAll()
                .toSet()
                .associateBy { it.id }

            action {
                users = loadedUsers
            }
        }
    }

    override fun RBuilder.render() {
        ringHeader {
            styledA("/") {
                css {
                    specific {
                        paddingLeft = 48.px
                    }
                }
                ringLogo {
                    attrs {
                        className = "logo"
                        glyph = jetbrainsLogo
                    }
                }
            }
        }

        styledDiv {
            css {
                +ApplicationStyles.wrapper
            }
            console.log("render postWithComments")
            postWithComments.map { postWithComments ->
                styledDiv {
                    css {
                        +ApplicationStyles.post
                    }
                    postView(
                        postWithComments,
                        users[postWithComments.post.userId],
                        onMoreComments = {
                            onMoreComment(postWithComments.post.id)
                        })
                }
            }
        }
    }

    private fun onMoreComment(postId: Int) {
        val commentsService = CommentsService(coroutineContext)
        val post = postWithComments.find { it.post.id == postId }

        if (post != null) {
            props.coroutineScope.launch {
                val comments = commentsService.getComments(postId.toString(), Random.nextInt(4))

                action {
                    postWithComments = postWithComments.map {
                        if (it != post) it else PostWithComments(it.post, it.comments + comments)
                    }
                }
            }
        }
    }
}
