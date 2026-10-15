// BottomSheet.kt

package com.example.musicfy.ui.component
import android.os.SystemClock
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.compositionLocalOf

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.musicfy.LocalPlayerConnection
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.musicfy.constants.NavigationBarAnimationSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.pow

private val PlayerSheetAnimationSpec = spring<Dp>(
    dampingRatio = 0.96f,
    stiffness = 80f
)

/**
 * How far past the touch slop a drag has to go, summed over the whole drag, before the sheet
 * opens or closes for it - on top of the old per-move bar, which still acts as soon as it did.
 */
private val SheetSwipeCommit = 8.dp

private val PlayerSheetHorizontalAnimationSpec = spring<Float>(
    dampingRatio = 0.92f,
    stiffness = 90f
)

@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    background: @Composable (BoxScope.() -> Unit) = { },
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    isExpandable: Boolean = true,
    isPillTransition: Boolean = false,
    pureBlack: Boolean = false,
    /** Offered vertical swipes on the fully expanded sheet first (pill transition only). */
    expandedSwipe: ExpandedSwipe? = null,
    sharedContent: @Composable (BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val currentSwipe by androidx.compose.runtime.rememberUpdatedState(expandedSwipe)

    if (!isPillTransition) {
        Box(
            modifier = modifier
                .graphicsLayer {

                    alpha = (1.4f * (state.progress.coerceAtLeast(0.1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)
                }
                .fillMaxSize(),
            content = background
        )
    }
        if (!isPillTransition) {
            val sheetCornerShape = remember { RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) }
            // Latches once the sheet has been opened, so later drags reuse the composed content.
            var contentEverShown by remember { mutableStateOf(false) }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val y = (state.expandedBound - state.value).toPx().coerceAtLeast(0f)
                        translationY = y
                        // Pre-built shapes: this block runs every frame of the drag, and
                        // allocating a RoundedCornerShape per frame is pure churn.
                        shape = if (!state.isExpanded) sheetCornerShape else RectangleShape
                        clip = true
                    }
                    .pointerInput(state, isExpandable) {
                        if (!isExpandable) return@pointerInput
                        val velocityTracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                velocityTracker.addPointerInputChange(change)
                                if (dragAmount < -3f && state.isCollapsed) {
                                    state.expandSoft()
                                    change.consume()
                                } else if (dragAmount > 3f && state.isExpanded) {
                                    state.collapseSoft()
                                    change.consume()
                                } else if (dragAmount > 3f && state.isCollapsed && onDismiss != null) {
                                    state.dismiss()
                                    onDismiss.invoke()
                                    change.consume()
                                }
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                            },
                            onDragEnd = {
                                velocityTracker.resetTracking()
                            }
                        )
                    }
            ) {
                if (!state.isCollapsed && !state.isDismissed) {
                    BackHandler(onBack = state::collapseSoft)
                }

                // Composing the sheet content is expensive, and gating it on `!isCollapsed`
                // meant the whole player subtree was built the instant a drag started - the
                // stutter in the first few pixels of a swipe up. Once it has been shown, keep
                // it composed; alpha already hides it while collapsed.
                if (!state.isCollapsed && !contentEverShown) {
                    SideEffect { contentEverShown = true }
                }
                if (contentEverShown || !state.isCollapsed) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = ((state.progress - 0.15f) * 4).coerceIn(0f, 1f)
                            },
                        content = content
                    )
                }

                if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = 1f - (state.progress * 4).coerceAtMost(1f)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (isExpandable) state.expandSoft() },
                            )
                            .fillMaxWidth()
                            .height(state.collapsedBound),
                        content = collapsedContent,
                    )
                }

                if (sharedContent != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        content = sharedContent
                    )
                }
            }
        } else {

            val playerConnection = LocalPlayerConnection.current

            val coroutineScope = rememberCoroutineScope()
            val animationSpec = remember {
                PlayerSheetHorizontalAnimationSpec
            }
            var dragStartTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            var totalHorizontalDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

            val sheetCoordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }

            val sheetClipShape = remember {
                object : androidx.compose.ui.graphics.Shape {
                    var hp = 0f
                    var visibleHeight = 0f
                    var cr = 0f

                    override fun createOutline(
                        size: androidx.compose.ui.geometry.Size,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        density: androidx.compose.ui.unit.Density
                    ): androidx.compose.ui.graphics.Outline {
                        return androidx.compose.ui.graphics.Outline.Rounded(
                            androidx.compose.ui.geometry.RoundRect(
                                left = hp,
                                top = 0f,
                                right = size.width - hp,
                                bottom = visibleHeight,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr, cr)
                            )
                        )
                    }
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .nestedScroll(remember(state) { state.preUpPostDownNestedScrollConnection })
            ) {

                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(state.expandedBound)
                        .graphicsLayer {
                            val p = state.progress.coerceIn(0f, 1f)
                            sheetClipShape.cr = androidx.compose.ui.unit.lerp(20.dp, 0.dp, p).toPx()
                            sheetClipShape.hp = androidx.compose.ui.unit.lerp(24.dp, 0.dp, p).toPx()
                            sheetClipShape.visibleHeight = androidx.compose.ui.unit.lerp(64.dp, state.expandedBound, p).toPx()

                            shape = sheetClipShape
                            clip = true
                            translationY = (state.expandedBound - state.value).toPx()
                            translationX = state.horizontalOffset
                        }
                        .drawWithContent {
                            drawContent()
                            val p = state.progress.coerceIn(0f, 1f)
                            if (p < 0.05f) {
                                val cr = androidx.compose.ui.unit.lerp(20.dp, 0.dp, p).toPx()
                                val hp = androidx.compose.ui.unit.lerp(24.dp, 0.dp, p).toPx()
                                val visibleHeight = androidx.compose.ui.unit.lerp(64.dp, state.expandedBound, p).toPx()
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.1f),
                                    topLeft = androidx.compose.ui.geometry.Offset(hp, 0f),
                                    size = androidx.compose.ui.geometry.Size(size.width - hp * 2, visibleHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr, cr),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                                )
                            }
                        }
                        .pointerInput(state, isExpandable) {
                            if (!isExpandable) return@pointerInput
                            // A tap on the collapsed sheet opens it. Open, a touch is left alone:
                            // taking it here would leave nothing for the player's background hold
                            // to tell apart from a button's.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (!state.isCollapsed) return@awaitEachGesture
                                down.consume()
                                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                up.consume()
                                if (state.isCollapsed) coroutineScope.launch { state.expandSoft() }
                            }
                        }
                        // Where the sheet's own coordinates are, so a drag's start can be handed
                        // to the swipe handler in root coordinates. Stored, not state: it is only
                        // read when a drag starts.
                        .onPlaced { sheetCoordinates[0] = it }
                        .pointerInput(state, isExpandable) {
                            if (!isExpandable) return@pointerInput
                            val velocityTracker = VelocityTracker()
                            // Set once a swipe on the expanded sheet has been handed over; from then
                            // on the whole drag, both directions, goes to that handler.
                            var swipe: ExpandedSwipe? = null
                            // Where the drag began, in root coordinates.
                            var swipeStart = Offset.Zero
                            // The whole drag so far. Each move alone used to have to pass 2px for
                            // the sheet to act, and a screen that reports touches more often (120Hz
                            // and up, as on newer Samsungs) splits a steady swipe into moves too
                            // small to ever pass it: the player wouldn't swipe closed.
                            var dragSum = Offset.Zero
                            val commitPx = SheetSwipeCommit.toPx()
                            // Whether this drag began on the open player (or one all but settled
                            // open: the open spring's tail runs on for a second after it looks up).
                            var startedOpen = false
                            // The sheet was sent on its way by this drag: once is enough.
                            var acted = false

                            detectDragGestures(
                                onDragStart = { start ->
                                    dragStartTime = System.currentTimeMillis()
                                    totalHorizontalDrag = 0f
                                    velocityTracker.resetTracking()
                                    swipe = null
                                    dragSum = Offset.Zero
                                    acted = false
                                    startedOpen = state.isExpanded ||
                                        (state.isExpandedOrExpanding && state.progress > 0.9f)
                                    val coordinates = sheetCoordinates[0]
                                    swipeStart = if (coordinates != null && coordinates.isAttached) {
                                        coordinates.localToRoot(start)
                                    } else {
                                        start
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    velocityTracker.addPointerInputChange(change)
                                    dragSum += dragAmount
                                    val vertical = abs(dragSum.y) > abs(dragSum.x)
                                    val pulledUp = vertical && dragSum.y < -commitPx
                                    val pulledDown = vertical && dragSum.y > commitPx

                                    val claimed = swipe ?: currentSwipe?.takeIf { handler ->
                                        state.isExpanded && handler.onStart(swipeStart, dragAmount)
                                    }?.also { swipe = it }

                                    if (claimed != null) {
                                        claimed.onDrag(dragAmount.y)
                                        change.consume()
                                    } else if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) && state.isCollapsed) {
                                        totalHorizontalDrag += dragAmount.x
                                        val resistance = 1f - (kotlin.math.abs(state.horizontalOffset) / (size.width / 2f)).coerceIn(0f, 0.8f)
                                        state.horizontalOffset += dragAmount.x * resistance
                                    } else {
                                        if ((dragAmount.y < -2f || pulledUp) && state.isCollapsed) {
                                            state.expandSoft()
                                            change.consume()
                                        } else if (
                                            !acted && startedOpen && (dragAmount.y > 2f || pulledDown) &&
                                            !state.isCollapsed &&
                                            currentSwipe?.canCollapseFrom(swipeStart) != false
                                        ) {
                                            acted = true
                                            state.collapseSoft()
                                            change.consume()
                                        } else if (dragAmount.y > 2f && state.isCollapsed && onDismiss != null) {
                                            state.dismiss()
                                            onDismiss.invoke()
                                            change.consume()
                                        }
                                    }
                                },
                                onDragCancel = {
                                    velocityTracker.resetTracking()
                                    swipe?.onEnd(0f)
                                    swipe = null
                                    coroutineScope.launch {
                                        state.animateHorizontalOffsetTo(0f)
                                    }
                                },
                                onDragEnd = {
                                    swipe?.let { handler ->
                                        handler.onEnd(velocityTracker.calculateVelocity().y)
                                        velocityTracker.resetTracking()
                                        swipe = null
                                        return@detectDragGestures
                                    }
                                    velocityTracker.resetTracking()

                                    if (state.isCollapsed || state.progress < 0.2f) {
                                        val dragDuration = System.currentTimeMillis() - dragStartTime
                                        val horizontalVelocity = if (dragDuration > 0) totalHorizontalDrag / dragDuration else 0f
                                        val currentOffset = state.horizontalOffset

                                        val shouldChangeSong = (kotlin.math.abs(currentOffset) > 50f && kotlin.math.abs(horizontalVelocity) > 2f) ||
                                                (kotlin.math.abs(currentOffset) > size.width / 3f)

                                        if (shouldChangeSong && playerConnection != null) {
                                            if (currentOffset > 0) playerConnection.player.seekToPreviousMediaItem()
                                            else playerConnection.player.seekToNext()
                                        }
                                    }

                                    coroutineScope.launch { state.animateHorizontalOffsetTo(0f) }
                                }
                            )
                        }
                ) {

                    val showBackground by remember { derivedStateOf { !state.isDismissed } }
                    val showControls by remember { derivedStateOf { !state.isDismissed } }

                    if (showBackground) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {

                                    val p = state.progress.coerceIn(0f, 1f)
                                    scaleX = 1.10f - (0.10f * p)
                                    scaleY = 1.10f - (0.10f * p)
                                }
                        ) {
                            background()
                        }
                    }

                    if (sharedContent != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            content = sharedContent
                        )
                    }

                    if (showControls) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {

                                    alpha = ((state.progress.coerceIn(0f, 1f) - 0.25f) / 0.60f).coerceIn(0f, 1f)
                                }
                        ) {
                            content()
                        }
                    }
                }

                if (!isPillTransition && !state.isExpanded && (onDismiss == null || !state.isDismissed)) {
                    Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(state.collapsedBound)
                            .graphicsLayer {
                                val p = state.progress.coerceIn(0f, 1f)
                                alpha = (1f - (p / 0.3f)).coerceIn(0f, 1f)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (isExpandable) state.expandSoft() },
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.TopStart
                    ) {
                        collapsedContent()
                    }
                }

                if (!state.isCollapsed && !state.isDismissed) {
                    BackHandler(onBack = state::collapseSoft)
                }
            }
        }
    }

val LocalPlayerBottomSheetState = compositionLocalOf<BottomSheetState?> { null }

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
    /** How far a list pulled past its top drags the sheet closed, px (see the nested scroll). */
    private val collapsePullPx: Float = 24f,
    /**
     * Where the sheet was last sent ([expandedAnchor], [collapsedAnchor], [dismissedAnchor]). Kept
     * by [rememberBottomSheetState] across the new states it makes when the bounds change, so it
     * holds through a resize, while the value catches up with the new bounds.
     */
    private val anchor: () -> Int = { -1 },
) : DraggableState by draggableState {
    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    /**
     * Open, or on its way there. [isExpanded] only holds once the open spring has settled to the
     * exact pixel - a good second after the player looks open - and it drops whenever the window's
     * height changes under it, until the sheet has caught up. What the open player's own state
     * hangs on (its menus, whether a swipe down closes it) goes by this, so neither is lost then.
     */
    val isExpandedOrExpanding by derivedStateOf { anchor() == expandedAnchor }

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    var horizontalOffset by androidx.compose.runtime.mutableFloatStateOf(0f)
        internal set

    suspend fun animateHorizontalOffsetTo(targetValue: Float) {
        androidx.compose.animation.core.animate(
            initialValue = horizontalOffset,
            targetValue = targetValue,
            animationSpec = PlayerSheetHorizontalAnimationSpec
        ) { value, _ ->
            horizontalOffset = value
        }
    }

    /**
     * Timestamped ring buffer for the frozen-sheet investigation (debug readout + COPY LOG).
     * Plain var on purpose: written from presses, drives and recreations without spamming
     * recomposition; read only when copying.
     */
    var eventLog: String = ""

    fun logEvent(msg: String) {
        val t = SystemClock.uptimeMillis() % 100000
        eventLog = "$t $msg\n$eventLog".take(4000)
    }

    /** True while this state's coroutine scope can still launch sheet animations. */
    val scopeAlive: Boolean get() = coroutineScope.isActive

    /**
     * Debug drive tracker (read by the player's debug readout): which call last drove the
     * sheet animation and toward what, and whether that drive arrived, was cancelled by a
     * competing animation, or never ran. Decisive for frozen-sheet bugs: anchor flips but
     * value never moves.
     */
    var lastDrive: String by mutableStateOf("none")
    var lastDriveResult: String by mutableStateOf("-")

    private var driveId = 0L

    private fun drive(tag: String, target: Dp, spec: AnimationSpec<Dp> = spring(), expectedAnchor: Int) {
        // One press can reach collapseSoft through several enabled back handlers at once
        // (outer, inner, the sheet's own). Restarting an identical in-flight drive churns
        // the animation into roughness, so a same-target drive that is already running wins
        // and the duplicate is dropped. A different target still redirects as before.
        if (animatable.isRunning && animatable.targetValue == target) {
            logEvent("drive $tag target=${target.value.toInt()} DEDUPED (already running)")
            return
        }
        val id = ++driveId
        val startValue = animatable.value
        lastDrive = "$tag>${target.value.toInt()}"
        lastDriveResult = "started"
        logEvent("drive#$id $tag target=${target.value.toInt()} from=${startValue.value.toInt()} anchor=$expectedAnchor")
        coroutineScope.launch {
            try {
                animatable.animateTo(target, spec)
                if (id == driveId) lastDriveResult = "arrived"
                logEvent("drive#$id $tag arrived at=${animatable.value.value.toInt()}")
            } catch (e: CancellationException) {
                if (id == driveId) lastDriveResult = "cancelled"
                logEvent("drive#$id $tag CANCELLED at=${animatable.value.value.toInt()}")
                throw e
            }
        }
        // Watchdog: if the value is byte-for-byte frozen far from target while the anchor
        // still demands this drive (dropped/cancelled animation the caller never hears
        // about), snap there. A live drag or a running animation moves value, so only a
        // truly wedged sheet heals. This is what unfreezes the player on affected phones.
        coroutineScope.launch {
            kotlinx.coroutines.delay(600)
            if (id == driveId && anchor() == expectedAnchor &&
                (animatable.value - startValue).value.absoluteValue < 0.5f &&
                (animatable.value - target).value.absoluteValue > 1f
            ) {
                animatable.snapTo(target)
                lastDriveResult = "healed"
                logEvent("drive#$id WATCHDOG snap to=${target.value.toInt()}")
            }
        }
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        onAnchorChanged(collapsedAnchor)
        drive("collapse", collapsedBound, animationSpec, collapsedAnchor)
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        onAnchorChanged(expandedAnchor)
        drive("expand", animatable.upperBound!!, animationSpec, expandedAnchor)
    }

    private fun collapse() {
        collapse(PlayerSheetAnimationSpec)
    }

    private fun expand() {
        expand(PlayerSheetAnimationSpec)
    }

    fun collapseSoft() {
        collapse(PlayerSheetAnimationSpec)
    }

    fun expandSoft() {
        expand(PlayerSheetAnimationSpec)
    }

    fun dismiss() {
        onAnchorChanged(dismissedAnchor)
        drive("dismiss", animatable.lowerBound!!, expectedAnchor = dismissedAnchor)
    }

    suspend fun dismissAndWait() {
        onAnchorChanged(dismissedAnchor)
        lastDrive = "dismissAndWait>${animatable.lowerBound!!.value.toInt()}"
        lastDriveResult = "started"
        try {
            animatable.animateTo(animatable.lowerBound!!)
            lastDriveResult = "arrived"
        } catch (e: CancellationException) {
            lastDriveResult = "cancelled"
            throw e
        }
    }

    fun snapTo(value: Dp) {
        lastDrive = "snapTo>${value.value.toInt()}"
        lastDriveResult = "started"
        coroutineScope.launch {
            try {
                animatable.snapTo(value)
                lastDriveResult = "arrived"
            } catch (e: CancellationException) {
                lastDriveResult = "cancelled"
                throw e
            }
        }
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
        if (velocity > 250) {
            expand()
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse()
            }
        } else {
            val l0 = dismissedBound
            val l1 = dismissedBound + (collapsedBound - dismissedBound) / 2
            val l2 = expandedBound - (expandedBound - collapsedBound) / 6
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse()
                    }
                }

                in l1..l2 -> collapse()
                in l2..l3 -> expand()
                else -> Unit
            }
        }
    }

    /**
     * Whether a list scrolled past its top drags the sheet closed. The player turns this off while
     * its queue is open, so pulling down at the top of the queue only stretches the list.
     */
    var collapseOnNestedOverscroll: Boolean = true

    /**
     * Back chain the player publishes for the outer handler in MainActivity (which is composed
     * after navigation, so it beats it). Returns true when the press was consumed. Plain vars
     * would go stale in `enabled`; hence snapshot state. Cleared when the player leaves
     * composition, so a stale chain never swallows back.
     */
    var backHandler: (() -> Boolean)? by mutableStateOf(null)

    /** Which player instance published [backHandler]; guards the clear on dispose. */
    var backOwner: Any? = null

    /** True while the player holds a modal that owns back with its own animated dismiss. */
    var backModalOpen: Boolean by mutableStateOf(false)

    val preUpPostDownNestedScrollConnection
        get() = object : NestedScrollConnection {
            var isTopReached = false
            // Pulled past the list's top so far, summed: a fast screen hands the pull over in
            // moves too small to pass the per-move bar one at a time (see the sheet's own drag).
            var pulled = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isExpanded && available.y < 0) {
                    isTopReached = false
                    pulled = 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isTopReached) {
                    isTopReached = consumed.y == 0f && available.y > 0
                }
                if (isTopReached && source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                }

                return if (
                    collapseOnNestedOverscroll && isTopReached &&
                    source == NestedScrollSource.UserInput &&
                    (available.y > 3f || (available.y > 0f && pulled > collapsePullPx))
                ) {
                    collapseSoft()
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (collapseOnNestedOverscroll && isTopReached && available.y > 100f) {
                    collapseSoft()
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isTopReached = false
                pulled = 0f
                return Velocity.Zero
            }
        }
}

const val expandedAnchor = 2
const val collapsedAnchor = 1
const val dismissedAnchor = 0

/**
 * True on gesture navigation (any mandatory system-gesture edge inset), false on 3-button nav.
 *
 * Reactive snapshot state, unlike a one-shot tappable-element read. The player keeps the system
 * navigation bar visible on gesture nav: hiding it eats the first back swipe on several OEM
 * skins (Vivo/Funtouch included), which is exactly "back works on some phones, not others".
 */
@Composable
fun rememberIsGestureNav(): Boolean {
    val paddings = WindowInsets.systemGestures.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return remember(paddings, layoutDirection) {
        paddings.calculateLeftPadding(layoutDirection) +
            paddings.calculateRightPadding(layoutDirection) +
            paddings.calculateBottomPadding() > 0.dp
    }
}

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = dismissedAnchor,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable = remember {
        Animatable(0.dp, Dp.VectorConverter)
    }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
        val initialValue = when (previousAnchor) {
            expandedAnchor -> expandedBound
            collapsedAnchor -> collapsedBound
            dismissedAnchor -> dismissedBound
            else -> error("Unknown BottomSheet anchor")
        }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)

        val state = BottomSheetState(
            draggableState = DraggableState { delta ->

                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                }
            },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound,
            collapsePullPx = with(density) { SheetSwipeCommit.toPx() },
            anchor = { previousAnchor },
        )
        state.lastDrive = "init>${initialValue.value.toInt()}"
        state.lastDriveResult = "started"
        state.logEvent("recreate bounds dis=${dismissedBound.value.toInt()} col=${collapsedBound.value.toInt()} exp=${expandedBound.value.toInt()} anchor=$previousAnchor val=${animatable.value.value.toInt()}")
        coroutineScope.launch {
            try {
                animatable.animateTo(initialValue, NavigationBarAnimationSpec)
                state.lastDriveResult = "arrived"
                state.logEvent("init arrived at=${animatable.value.value.toInt()}")
            } catch (e: CancellationException) {
                state.lastDriveResult = "cancelled"
                state.logEvent("init CANCELLED at=${animatable.value.value.toInt()}")
                throw e
            }
        }
        state
    }
}

/**
 * Offered every vertical swipe on the fully expanded sheet before the sheet itself acts on it. The
 * player uses it to pull a page up out of its bottom card, to send a page back down, and to keep
 * swipes over its controls from closing it. Children that handle their own drags (the card's flip,
 * a list, the seek bar, the cover's skip swipe) consume them first, so only the free parts of the
 * sheet get here.
 */
interface ExpandedSwipe {
    /**
     * A swipe has begun at [start] (root coordinates) and moved by [drag] (px) so far. Called on
     * each move until it returns true; once it does, the whole drag goes here.
     */
    fun onStart(start: Offset, drag: Offset): Boolean

    /** [dy] in px, negative going up. */
    fun onDrag(dy: Float)

    /** [velocityY] in px/s, negative going up; 0 when the gesture was cancelled. */
    fun onEnd(velocityY: Float)

    /**
     * False where a swipe down must not close the sheet even though [onStart] didn't take it -
     * a diagonal drag over controls that have swipes of their own, say.
     */
    fun canCollapseFrom(start: Offset): Boolean = true
}
