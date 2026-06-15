package com.bcon.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.bcon.messenger.ui.theme.LocalBeaconColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelFeedScreen(
    channelId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val c = LocalBeaconColors.current
    val bgGradient = Brush.verticalGradient(listOf(c.gradientStart, c.gradientEnd))
    val scope = rememberCoroutineScope()

    var channel by remember { mutableStateOf<Channel?>(null) }
    var posts by remember { mutableStateOf(listOf<ChannelPost>()) }
    var postText by remember { mutableStateOf("") }
    val userId = remember { UserStorage.getUserId(context) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val b64 = ImageHelper.prepareImageSingle(context, uri)
                val text = postText.trim()
                val authorName = UserStorage.getUserDisplayName(context)
                val post = ChannelPost(
                    id = UUID.randomUUID().toString(),
                    channelId = channelId,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    authorId = userId,
                    authorName = authorName,
                    imageData = b64
                )
                withContext(Dispatchers.Main) { postText = "" }
                context.startService(
                    Intent(context, MessengerService::class.java).apply {
                        putExtra("channel_post_id", channelId)
                        putExtra("channel_post_text", text)
                        putExtra("channel_post_msg_id", post.id)
                        putExtra("channel_post_image", b64)
                    }
                )
                ChannelManager.addPost(context, post)
                withContext(Dispatchers.Main) {
                    posts = ChannelManager.loadPosts(context, channelId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(s.channelLoadImageError)
                }
            }
        }
    }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            val ch = ChannelManager.getChannel(context, channelId)
            val ps = ChannelManager.loadPosts(context, channelId)
            withContext(Dispatchers.Main) {
                channel = ch
                posts = ps
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    LaunchedEffect(posts.size) {
        if (posts.isNotEmpty()) {
            listState.animateScrollToItem(posts.size - 1)
        }
    }

    val ch = channel

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Channel avatar
                        Surface(
                            shape = CircleShape,
                            color = c.primaryBlue,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(ch?.avatar ?: "📢", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                ch?.name ?: s.channelDefault,
                                color = Color.White,
                                fontFamily = JetBrainsMono,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!ch?.description.isNullOrBlank()) {
                                Text(
                                    ch!!.description,
                                    color = c.textPrimary.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMono,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = s.back,
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (ch?.isAdmin == true) {
                        // Admin: copy subscribe link
                        TextButton(onClick = {
                            val link = ChannelManager.generateSubscribeLink(
                                channelId, ch.name, ch.avatar
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("channel_link", link))
                            scope.launch {
                                snackbarHostState.showSnackbar(s.channelLinkCopied)
                            }
                        }) {
                            Text(
                                s.channelCopyLink,
                                color = c.accent,
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMono
                            )
                        }
                    } else if (ch != null) {
                        // Subscriber: unsubscribe
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                // Send unsubscribe to server
                                context.startService(
                                    Intent(context, MessengerService::class.java).apply {
                                        putExtra("channel_unsubscribe", channelId)
                                    }
                                )
                                ChannelManager.removeChannel(context, channelId)
                                withContext(Dispatchers.Main) { onBack() }
                            }
                        }) {
                            Text(
                                s.channelUnsubscribe,
                                color = Color(0xFFFF4444),
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMono
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.topBar)
            )
        },
        bottomBar = {
            // Only admin can post
            if (ch?.isAdmin == true) {
                Surface(
                    color = c.topBar,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attach image button — auto-sends with current text
                        IconButton(onClick = { imageLauncher.launch("image/*") }) {
                            Text("📎", fontSize = 22.sp)
                        }
                        OutlinedTextField(
                            value = postText,
                            onValueChange = { postText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    s.channelPostPlaceholder,
                                    color = c.textPrimary.copy(alpha = 0.4f),
                                    fontFamily = JetBrainsMono,
                                    fontSize = 14.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = c.accent,
                                unfocusedBorderColor = c.textPrimary.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = c.accent
                            ),
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Send text-only post
                        IconButton(
                            onClick = {
                                val text = postText.trim()
                                if (text.isBlank()) return@IconButton
                                postText = ""
                                val authorName = UserStorage.getUserDisplayName(context)
                                val post = ChannelPost(
                                    id = UUID.randomUUID().toString(),
                                    channelId = channelId,
                                    text = text,
                                    timestamp = System.currentTimeMillis(),
                                    authorId = userId,
                                    authorName = authorName
                                )
                                context.startService(
                                    Intent(context, MessengerService::class.java).apply {
                                        putExtra("channel_post_id", channelId)
                                        putExtra("channel_post_text", text)
                                        putExtra("channel_post_msg_id", post.id)
                                    }
                                )
                                scope.launch(Dispatchers.IO) {
                                    ChannelManager.addPost(context, post)
                                    withContext(Dispatchers.Main) {
                                        posts = ChannelManager.loadPosts(context, channelId)
                                    }
                                }
                            }
                        ) {
                            Text("➤", fontSize = 26.sp, color = c.accent)
                        }
                    }
                }
            }
        },
        containerColor = c.inputBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(padding)
        ) {
            if (posts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📢", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        s.chatsNoPosts,
                        color = c.textPrimary.copy(alpha = 0.5f),
                        fontFamily = JetBrainsMono,
                        fontSize = 16.sp
                    )
                    if (ch?.isAdmin == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            s.channelWriteFirst,
                            color = c.textPrimary.copy(alpha = 0.35f),
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        ChannelPostCard(
                            post = post,
                            channelAvatar = ch?.avatar ?: "📢",
                            channelName = ch?.name ?: ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelPostCard(post: ChannelPost, channelAvatar: String, channelName: String) {
    val c = LocalBeaconColors.current
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val postBitmap = remember(post.id) {
        if (post.imageData.isNotEmpty()) ImageHelper.decodeBase64ToBitmap(post.imageData) else null
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channelAvatar, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    channelName,
                    color = c.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    dateFormat.format(Date(post.timestamp)),
                    color = c.textPrimary.copy(alpha = 0.45f),
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp
                )
            }
            if (post.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    post.text,
                    color = c.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            if (postBitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    bitmap = postBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

// ─── Subscribe Preview Dialog ─────────────────────────────────────────────────
// Shown when a user opens a beacon://channel link (paste or tap)

@Composable
fun ChannelSubscribeDialog(
    linkData: ChannelManager.ChannelLinkData,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val c = LocalBeaconColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = c.topBar,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(linkData.channelAvatar, fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        s.channelDefault,
                        color = c.accent,
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMono
                    )
                    Text(
                        linkData.channelName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMono,
                        fontSize = 18.sp
                    )
                }
            }
        },
        text = {
            Text(
                s.channelSubscribeConfirm,
                color = c.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onSubscribe,
                colors = ButtonDefaults.buttonColors(containerColor = c.primaryBlue)
            ) {
                Text(s.channelSubscribeBtn, color = Color.White, fontFamily = JetBrainsMono)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel, color = c.textPrimary.copy(alpha = 0.6f), fontFamily = JetBrainsMono)
            }
        }
    )
}
