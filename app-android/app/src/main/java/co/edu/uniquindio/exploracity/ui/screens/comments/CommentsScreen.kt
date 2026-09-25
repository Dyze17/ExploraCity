package co.edu.uniquindio.exploracity.ui.screens.comments

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.uniquindio.exploracity.R
import co.edu.uniquindio.exploracity.data.repository.sampleComments
import co.edu.uniquindio.exploracity.data.repository.sampleCurrentUser
import co.edu.uniquindio.exploracity.data.repository.samplePois
import co.edu.uniquindio.exploracity.domain.model.Author
import co.edu.uniquindio.exploracity.domain.model.Comment
import co.edu.uniquindio.exploracity.ui.components.EmptyState
import co.edu.uniquindio.exploracity.ui.components.EmptyStateTone
import co.edu.uniquindio.exploracity.ui.components.ExploraButton
import co.edu.uniquindio.exploracity.ui.components.ExploraButtonStyle
import co.edu.uniquindio.exploracity.ui.components.ExploraTopAppBar
import co.edu.uniquindio.exploracity.ui.components.InitialsAvatar
import co.edu.uniquindio.exploracity.ui.components.LevelChip
import co.edu.uniquindio.exploracity.ui.components.LoadMoreFailedRow
import co.edu.uniquindio.exploracity.ui.components.LoadingMoreRow
import co.edu.uniquindio.exploracity.ui.components.SkeletonBlock
import co.edu.uniquindio.exploracity.ui.components.labelRes
import co.edu.uniquindio.exploracity.ui.components.rememberShimmerBrush
import co.edu.uniquindio.exploracity.ui.components.scaledWithFont
import co.edu.uniquindio.exploracity.ui.theme.ExploraCityTheme
import co.edu.uniquindio.exploracity.ui.theme.ThemeMode
import co.edu.uniquindio.exploracity.ui.theme.exploraColors
import co.edu.uniquindio.exploracity.util.RelativeTime
import co.edu.uniquindio.exploracity.util.formatDate
import co.edu.uniquindio.exploracity.util.relativeTime
import co.edu.uniquindio.exploracity.viewmodel.CommentsContent
import co.edu.uniquindio.exploracity.viewmodel.CommentsUiState
import co.edu.uniquindio.exploracity.viewmodel.CommentsViewModel
import co.edu.uniquindio.exploracity.viewmodel.OwnComment
import co.edu.uniquindio.exploracity.viewmodel.SendStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/** 14 · Comentarios, conectado a su ViewModel. */
@Composable
fun CommentsRoute(onBack: () -> Unit, viewModel: CommentsViewModel = viewModel(factory = CommentsViewModel.factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CommentsScreen(
        state = state,
        startWriting = viewModel.startWriting,
        callbacks = CommentsCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onLoadMore = viewModel::onLoadMore,
            onSend = viewModel::onSend,
            onRetrySend = viewModel::onRetrySend,
        ),
    )
}

class CommentsCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    /** Devuelve si se envió; si no, el campo conserva el texto. */
    val onSend: (String) -> Boolean = { false },
    val onRetrySend: (String) -> Unit = {},
)

/**
 * Lista del más reciente al más antiguo (como los dos publicados del lienzo; el lienzo pone el nuevo abajo, pero
 * contradice ese orden): el comentario propio aparece arriba y la lista sube hasta él. Con [startWriting] el campo
 * toma el foco al cargar.
 */
@Composable
fun CommentsScreen(state: CommentsUiState, callbacks: CommentsCallbacks, modifier: Modifier = Modifier, startWriting: Boolean = false) {
    val content = state.content
    val loaded = content as? CommentsContent.Loaded
    val listState = rememberLazyListState()
    ScrollToNewestOwn(state, listState)

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ExploraTopAppBar(
            title = stringResource(R.string.comments_title),
            subtitle = loaded?.let { stringResource(R.string.comments_subtitle, it.poiTitle, it.total) },
            subtitleDescription = loaded?.let { pluralStringResource(R.plurals.comments_subtitle_description, it.total, it.poiTitle, it.total) },
            onBack = callbacks.onBack,
            divider = true,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (content) {
                CommentsContent.Loading -> CommentsSkeleton()
                CommentsContent.Error -> Centered(aboveNavigationBar = true) {
                    EmptyState(
                        icon = R.drawable.ic_sync_problem,
                        title = stringResource(R.string.comments_error_title),
                        body = stringResource(R.string.comments_error_body),
                        tone = EmptyStateTone.WARNING,
                    ) {
                        ExploraButton(stringResource(R.string.action_retry), onClick = callbacks.onRetry, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_refresh)
                    }
                }
                CommentsContent.NotFound -> Centered(aboveNavigationBar = true) {
                    EmptyState(
                        icon = R.drawable.ic_location_off,
                        title = stringResource(R.string.detail_not_found_title),
                        body = stringResource(R.string.detail_not_found_body),
                    ) {
                        ExploraButton(stringResource(R.string.navigate_back), onClick = callbacks.onBack, modifier = Modifier.fillMaxWidth())
                    }
                }
                is CommentsContent.Loaded -> if (state.isEmpty) {
                    Centered(aboveNavigationBar = false) {
                        EmptyState(
                            icon = R.drawable.ic_chat_bubble,
                            title = stringResource(R.string.comments_empty_title),
                            body = stringResource(R.string.comments_empty_body),
                        )
                    }
                } else {
                    CommentList(content, state, callbacks, listState)
                }
            }
        }
        if (loaded != null) CommentComposer(startWriting, callbacks.onSend)
    }
}

/** Al enviar, la lista sube hasta el comentario nuevo. Solo una vez por comentario: rotar no vuelve a subir. */
@Composable
private fun ScrollToNewestOwn(state: CommentsUiState, listState: LazyListState) {
    val newest = state.own.firstOrNull()?.localId
    var scrolledTo by rememberSaveable { mutableStateOf(newest) }
    LaunchedEffect(newest) {
        if (newest != null && newest != scrolledTo) {
            scrolledTo = newest
            listState.animateScrollToItem(0)
        }
    }
}

@Composable
private fun Centered(aboveNavigationBar: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (aboveNavigationBar) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CommentList(content: CommentsContent.Loaded, state: CommentsUiState, callbacks: CommentsCallbacks, listState: LazyListState) {
    val loadMore by rememberUpdatedState(callbacks.onLoadMore)
    // Página siguiente al acercarse al final, como en el feed.
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }.distinctUntilChanged().filter { it }.collect { loadMore() }
    }
    val now by rememberNow()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(state.own, key = { "own-${it.localId}" }) { own -> OwnCommentItem(own, state.currentUser, now, callbacks.onRetrySend) }
        items(content.comments, key = { it.id }) { comment -> CommentItem(comment, now) }
        when {
            content.loadingMore -> item(key = "loading-more") { LoadingMoreRow(stringResource(R.string.comments_loading_more)) }
            content.loadMoreFailed -> item(key = "load-more-failed") {
                LoadMoreFailedRow(stringResource(R.string.comments_load_more_failed), onRetry = callbacks.onLoadMore)
            }
        }
    }
}

/** «hace 2 días» se actualiza solo mientras la pantalla está abierta. */
@Composable
private fun rememberNow() = produceState(Instant.now()) {
    while (true) {
        delay(30.seconds)
        value = Instant.now()
    }
}

@Composable
private fun timeText(createdAt: Instant, now: Instant): String = when (val time = relativeTime(createdAt, now, ZoneId.systemDefault())) {
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes_ago, time.count, time.count)
    is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours_ago, time.count, time.count)
    is RelativeTime.Days -> pluralStringResource(R.plurals.time_days_ago, time.count, time.count)
    is RelativeTime.On -> formatDate(time.date, withYear = !time.sameYear)
}

/** Comentario publicado: un solo nodo para el lector («María Paula, nivel Explorador, hace 2 días. El pan…»). */
@Composable
private fun CommentItem(comment: Comment, now: Instant) {
    val author = comment.author
    val time = timeText(comment.createdAt, now)
    val description = if (comment.mine) {
        stringResource(R.string.comments_mine_description, time, comment.text)
    } else {
        stringResource(R.string.comments_description, author.name, stringResource(author.level.labelRes), time, comment.text)
    }
    CommentLayout(author, comment.mine, Modifier.clearAndSetSemantics { contentDescription = description }, status = { MetaText(time) }) {
        CommentText(comment.text, MaterialTheme.exploraColors.textSecondary)
    }
}

/**
 * Comentario escrito aquí. El estado («Enviando…», «No se envió», la hora al publicarse) es un nodo aparte que se
 * anuncia al cambiar; al publicarse dice «Publicado» y no se repite cada minuto con la hora.
 */
@Composable
private fun OwnCommentItem(own: OwnComment, user: Author, now: Instant, onRetry: (String) -> Unit) {
    val description = stringResource(R.string.comments_own_description, own.text)
    val explora = MaterialTheme.exploraColors
    CommentLayout(
        user,
        mine = true,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        status = {
            when (own.status) {
                SendStatus.SENDING -> StatusLabel(R.drawable.ic_schedule, stringResource(R.string.comments_sending), explora.warningAccent)
                SendStatus.FAILED -> StatusLabel(R.drawable.ic_error, stringResource(R.string.comments_failed), MaterialTheme.colorScheme.error)
                SendStatus.SENT -> StatusLabel(null, timeText(own.createdAt, now), explora.textPlaceholder, spoken = stringResource(R.string.comments_sent))
            }
        },
    ) {
        CommentText(own.text, if (own.status == SendStatus.SENT) explora.textSecondary else explora.textPlaceholder)
        if (own.status == SendStatus.FAILED) {
            ExploraButton(
                stringResource(R.string.action_retry),
                onClick = { onRetry(own.localId) },
                // Corrido el relleno del botón para que el icono quede alineado con el texto del comentario.
                modifier = Modifier.offset(x = (-16).dp),
                style = ExploraButtonStyle.TEXT,
                icon = R.drawable.ic_refresh,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentLayout(
    author: Author,
    mine: Boolean,
    modifier: Modifier,
    status: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InitialsAvatar(author)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Nombre, nivel (README: visible en cada comentario) y, si es de la persona, «Tú»: el lienzo pone «Tú»
            // en lugar del nivel, pero el README pide el nivel siempre.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    author.name,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LevelChip(author.level)
                if (mine) YouChip()
                status()
            }
            body()
        }
    }
}

@Composable
private fun YouChip() {
    val scheme = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.comments_you),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700),
        color = MaterialTheme.exploraColors.iconSecondary,
        modifier = Modifier
            .background(scheme.surfaceContainerHigh, CircleShape)
            .border(1.dp, scheme.outline, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun MetaText(text: String, color: Color = MaterialTheme.exploraColors.textPlaceholder) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp), color = color)
}

@Composable
private fun StatusLabel(@DrawableRes icon: Int?, text: String, color: Color, spoken: String = text) {
    Row(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(13.dp.scaledWithFont()))
        MetaText(text, color)
    }
}

@Composable
private fun CommentText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = color)
}

/**
 * Campo de 14.a: cápsula sobre surfaceContainer y botón de enviar de 48 dp. Máximo 300 caracteres, con contador desde
 * 250; crece hasta 4 líneas y sube con el teclado. El texto sobrevive a la rotación y al cierre del proceso.
 */
@Composable
private fun CommentComposer(startWriting: Boolean, onSend: (String) -> Boolean) {
    val scheme = MaterialTheme.colorScheme
    val explora = MaterialTheme.exploraColors
    val textState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    var focusPending by rememberSaveable { mutableStateOf(startWriting) }
    LaunchedEffect(Unit) {
        if (focusPending) {
            focusPending = false
            focusRequester.requestFocus()
        }
    }
    val send = { if (onSend(textState.text.toString())) textState.clearText() }

    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(explora.divider))
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                CommentField(textState, Modifier.focusRequester(focusRequester))
                CharacterCounter(textState.text.length)
            }
            SendButton(enabled = textState.text.isNotBlank(), onClick = send)
        }
    }
}

@Composable
private fun CommentField(state: TextFieldState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val placeholder = stringResource(R.string.comments_placeholder)
    val label = stringResource(R.string.comments_field_label)
    BasicTextField(
        state = state,
        // El texto de ayuda es un hijo del campo y el lector no lo toma como su nombre (visto en el S20+).
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        inputTransformation = InputTransformation.maxLength(Comment.MAX_LENGTH),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        cursorBrush = SolidColor(scheme.primary),
        decorator = { innerTextField ->
            Box(
                Modifier
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.exploraColors.textPlaceholder,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                innerTextField()
            }
        },
    )
}

/** «262/300» desde 250 caracteres (README 14). No se anuncia en cada tecla: el lector ya repite lo escrito. */
@Composable
private fun CharacterCounter(length: Int) {
    if (length < Comment.COUNTER_FROM) return
    val spoken = stringResource(R.string.comments_counter_description, length, Comment.MAX_LENGTH)
    Text(
        stringResource(R.string.comments_counter, length, Comment.MAX_LENGTH),
        style = MaterialTheme.typography.bodySmall,
        // Llegar a 300 no es un error (el campo no deja pasar de ahí): sin rojo.
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, end = 12.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        textAlign = TextAlign.End,
    )
}

/** Deshabilitado con el campo vacío: mismos colores que los botones deshabilitados (textPlaceholder, 4.5:1). */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (enabled) scheme.primary else scheme.surfaceContainerHighest
    val content = if (enabled) scheme.onPrimary else MaterialTheme.exploraColors.textPlaceholder
    val description = stringResource(R.string.comments_send)
    Box(
        Modifier
            .size(48.dp)
            .background(container, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_send), null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/** Mientras carga: silueta de cuatro comentarios con un solo anuncio (como 11). */
@Composable
private fun CommentsSkeleton() {
    val brush = rememberShimmerBrush()
    val loading = stringResource(R.string.comments_loading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clearAndSetSemantics {
                contentDescription = loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        listOf(0.62f, 0.8f, 0.5f, 0.72f).forEach { lastLine ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(brush, Modifier.size(40.dp), CircleShape)
                Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(brush, Modifier.fillMaxWidth(0.45f).height(12.dp))
                    SkeletonBlock(brush, Modifier.fillMaxWidth().height(12.dp))
                    SkeletonBlock(brush, Modifier.fillMaxWidth(lastLine).height(12.dp))
                }
            }
        }
    }
}

private val previewCafe = samplePois.first()
private val previewNow = Instant.now()
private val previewState = CommentsUiState(
    currentUser = sampleCurrentUser,
    content = CommentsContent.Loaded(previewCafe.title, sampleComments(previewCafe, previewNow).take(2), total = 12, nextCursor = null),
    own = listOf(OwnComment("preview", "El patio es perfecto para trabajar temprano.", previewNow, SendStatus.SENDING)),
)

@Preview(name = "14.a · claro", widthDp = 360, heightDp = 800)
@Composable
private fun CommentsLightPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) { CommentsScreen(previewState, CommentsCallbacks()) }
}

@Preview(name = "14.a · oscuro · no se envió", widthDp = 360, heightDp = 800)
@Composable
private fun CommentsDarkPreview() {
    ExploraCityTheme(ThemeMode.DARK) {
        CommentsScreen(previewState.copy(own = previewState.own.map { it.copy(status = SendStatus.FAILED) }), CommentsCallbacks())
    }
}

@Preview(name = "14 · vacío", widthDp = 360, heightDp = 800)
@Composable
private fun CommentsEmptyPreview() {
    ExploraCityTheme(ThemeMode.LIGHT) {
        CommentsScreen(
            CommentsUiState(sampleCurrentUser, CommentsContent.Loaded("Galería Santa Fe", emptyList(), total = 0, nextCursor = null)),
            CommentsCallbacks(),
        )
    }
}
