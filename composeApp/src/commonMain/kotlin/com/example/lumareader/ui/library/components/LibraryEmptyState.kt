package com.example.lumareader.ui.library.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lumareader.theme.GoogleSans
import kotlinx.coroutines.delay

/**
 * Expressive empty state view displayed when the library contains no books.
 */
@Composable
fun LibraryEmptyState(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha))
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        var showTitle by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(150)
            showTitle = true
        }
        AnimatedVisibility(
            visible = showTitle,
            enter = slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Text(
                text = "Welcome to Luma",
                fontFamily = GoogleSans,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        var showBody by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(300)
            showBody = true
        }
        AnimatedVisibility(
            visible = showBody,
            enter = slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Text(
                text = "Import your EPUB digital files to begin cultivating your beautiful personal reading sanctuary.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
                lineHeight = 20.sp
            )
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        
        var showButton by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(450)
            showButton = true
        }
        AnimatedVisibility(
            visible = showButton,
            enter = slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
        ) {
            Button(
                onClick = onImportClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Select an ePub File", fontWeight = FontWeight.Bold)
            }
        }
    }
}
