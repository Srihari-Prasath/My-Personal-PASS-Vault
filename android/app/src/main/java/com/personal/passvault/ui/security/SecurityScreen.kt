package com.personal.passvault.ui.security

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.personal.passvault.ui.components.ConfirmDialog
import com.personal.passvault.ui.components.EntryAvatar
import com.personal.passvault.ui.components.SectionCard
import com.personal.passvault.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: SecurityViewModel = hiltViewModel()) {
    val report = vm.report
    var confirmBreach by remember { mutableStateOf(false) }
    // Re-check when coming back from fixing an entry
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Security check") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Rounded.Refresh, "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (report == null) return@Column
            ScoreRing(report.score, report.passwordCount, report.issueCount)

            BreachCard(vm.breach, onStart = { confirmBreach = true }, onOpen = onOpen)

            if (report.issueCount == 0) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text("No problems found. Nice!", style = MaterialTheme.typography.bodyLarge)
                }
            }
            IssueSection(Icons.Rounded.LockOpen, "Weak passwords", "Easy to guess — generate a new one", report.weak, onOpen)
            IssueSection(Icons.Rounded.ContentCopy, "Reused passwords", "One leak unlocks all of these", report.reused, onOpen)
            IssueSection(Icons.Rounded.History, "Old passwords", "Not changed in over a year", report.old, onOpen)
            IssueSection(Icons.Rounded.VpnKey, "Recovery codes", "Running out — generate a new set", report.lowCodes, onOpen)
            IssueSection(Icons.Rounded.CreditCard, "Cards", "Expired or expiring soon", report.cards, onOpen)
        }
    }

    if (confirmBreach) {
        ConfirmDialog(
            title = "Check for data breaches?",
            text = "Each password is turned into a SHA-1 fingerprint on this phone. Only the first 5 characters of that " +
                "fingerprint are sent to Have I Been Pwned (api.pwnedpasswords.com); the matching happens here. " +
                "Your passwords and account names never leave the phone.",
            confirmLabel = "Check now",
            onConfirm = vm::runBreachCheck,
            onDismiss = { confirmBreach = false },
        )
    }
}

@Composable
private fun ScoreRing(score: Int, passwords: Int, issues: Int) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(score) { sweep.animateTo(score / 100f, tween(1100, easing = FastOutSlowInEasing)) }
    val color by animateColorAsState(
        when {
            score >= 80 -> MaterialTheme.colorScheme.secondary
            score >= 50 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        },
        label = "scoreColor",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, 135f, 270f * sweep.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(sweep.value * 100).toInt()}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
                Text(
                    when {
                        score >= 80 -> "Strong"
                        score >= 50 -> "Fair"
                        else -> "Needs work"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "$passwords password${if (passwords == 1) "" else "s"} checked · $issues thing${if (issues == 1) "" else "s"} to fix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BreachCard(state: BreachState, onStart: () -> Unit, onOpen: (String) -> Unit) {
    SectionCard(title = "Data breaches") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedContent(
                targetState = when {
                    state.running -> 1
                    state.found != null -> 2
                    else -> 0
                },
                label = "breach",
            ) { stage ->
                when (stage) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "See if any of your passwords appear in known leaks. Off until you ask — nothing is sent automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check my passwords")
                        }
                    }
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val progress by animateFloatAsState(
                            if (state.total == 0) 0f else state.done.toFloat() / state.total, Motion.smooth(), label = "breachProgress",
                        )
                        Text("Checking ${state.done} of ${state.total}…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        val found = state.found.orEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (found.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.LockOpen, null,
                                    tint = if (found.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (found.isEmpty()) "None of your ${state.total} passwords were found in known breaches."
                                    else "${found.size} entr${if (found.size == 1) "y uses a" else "ies use"} leaked password${if (found.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            found.forEach { IssueRow(it, onOpen) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueSection(icon: ImageVector, title: String, explain: String, issues: List<Issue>, onOpen: (String) -> Unit) {
    if (issues.isEmpty()) return
    var open by rememberSaveable(title) { mutableStateOf(issues.size <= 3) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.smooth(), label = "chev")
    SectionCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp),
            ) { Text("${issues.size}", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation))
        }
        AnimatedVisibility(open, enter = expandVertically(Motion.size) + fadeIn(), exit = shrinkVertically(Motion.size) + fadeOut()) {
            Column(Modifier.padding(bottom = 6.dp)) { issues.forEach { IssueRow(it, onOpen) } }
        }
    }
}

@Composable
private fun IssueRow(issue: Issue, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(issue.entryId) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryAvatar(issue.icon, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(issue.title, style = MaterialTheme.typography.bodyLarge)
            Text(issue.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
