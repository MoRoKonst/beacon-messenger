package com.bcon.messenger

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bcon.messenger.ui.theme.LocalBeaconColors
import kotlinx.coroutines.launch

@Composable
fun PortholeSendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 44
) {
    val c = LocalBeaconColors.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    Box(
        modifier = modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(c.primaryBlue)
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = {
                        if (enabled) {
                            scope.launch {
                                scale.animateTo(
                                    targetValue = 0.82f,
                                    animationSpec = tween(100, easing = EaseIn)
                                )
                                scale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(150, easing = EaseOut)
                                )
                            }
                            onClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_send_arrow),
            contentDescription = "Отправить",
            tint = Color.White,
            modifier = Modifier.size((size * 0.55f).dp)
        )
    }
}
