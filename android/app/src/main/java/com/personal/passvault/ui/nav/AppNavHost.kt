package com.personal.passvault.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.passvault.core.crypto.VaultKeyManager
import com.personal.passvault.core.security.ClipboardHelper
import com.personal.passvault.core.session.VaultSession
import com.personal.passvault.data.icons.IconLoader
import com.personal.passvault.ui.backup.BackupScreen
import com.personal.passvault.ui.importcsv.ImportScreen
import com.personal.passvault.ui.kit.EmergencyKitScreen
import com.personal.passvault.ui.scan.ScanScreen
import com.personal.passvault.ui.security.SecurityScreen
import com.personal.passvault.ui.components.CopyPill
import com.personal.passvault.ui.components.LocalNavAnimatedScope
import com.personal.passvault.ui.components.LocalSecretGate
import com.personal.passvault.ui.components.SecretGate
import com.personal.passvault.ui.components.findFragmentActivity
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.personal.passvault.ui.components.LocalSharedTransitionScope
import com.personal.passvault.ui.detail.EntryDetailScreen
import com.personal.passvault.ui.edit.EntryEditScreen
import com.personal.passvault.ui.generator.GeneratorScreen
import com.personal.passvault.ui.settings.SettingsScreen
import com.personal.passvault.ui.setup.SetupScreen
import com.personal.passvault.ui.theme.LocalReduceMotion
import com.personal.passvault.ui.theme.Motion
import com.personal.passvault.ui.unlock.UnlockScreen
import com.personal.passvault.ui.vault.VaultListScreen

object Routes {
    const val SETUP = "setup"
    const val UNLOCK = "unlock"
    const val VAULT = "vault"
    const val DETAIL = "detail/{id}"
    const val EDIT = "edit?id={id}&type={type}"
    const val GENERATOR = "generator"
    const val SETTINGS = "settings"
    const val BACKUP = "backup?setup={setup}"
    const val SECURITY = "security"
    const val IMPORT = "import"
    const val SCAN = "scan"
    const val KIT = "kit"

    fun backup(setup: Boolean = false) = "backup?setup=$setup"
    fun detail(id: String) = "detail/$id"
    fun edit(id: String) = "edit?id=$id"
    fun add(type: String) = "edit?type=$type"
}

private const val SCAN_RESULT = "scan_result"

// ---- Telegram-style push/pop: new screen slides in from the right, old one drifts 25% left and dims ----
private typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun NavScope.pushEnter(): EnterTransition = slideInHorizontally(Motion.offset) { it }
private fun NavScope.pushExit(): ExitTransition = slideOutHorizontally(Motion.offset) { -it / 4 } + fadeOut(Motion.smooth(), targetAlpha = 0.5f)
private fun NavScope.popEnter(): EnterTransition = slideInHorizontally(Motion.offset) { -it / 4 } + fadeIn(Motion.smooth(), initialAlpha = 0.5f)
private fun NavScope.popExit(): ExitTransition = slideOutHorizontally(Motion.offset) { it }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(
    session: VaultSession,
    keyManager: VaultKeyManager,
    clipboard: ClipboardHelper,
    iconLoader: IconLoader,
    revealNeedsAuth: Boolean = false,
) {
    val nav = rememberNavController()
    val unlocked by session.unlocked.collectAsStateWithLifecycle()
    val clipState by clipboard.state.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current

    val start = when {
        !keyManager.vaultExists() -> Routes.SETUP
        session.unlocked.value -> Routes.VAULT
        else -> Routes.UNLOCK
    }

    // "Confirm it's you" before revealing secrets (Settings → Security). Holds auto-lock while the
    // phone's PIN screen is up, so "lock immediately" users don't get locked out mid-confirmation.
    val context = LocalContext.current
    val gateEnabled by rememberUpdatedState(revealNeedsAuth)
    val gate = remember(context) {
        SecretGate(
            activity = { context.findFragmentActivity() },
            enabled = { gateEnabled },
            beforePrompt = { session.holdAutoLock(60_000L) },
            afterPrompt = { session.releaseAutoLock() },
        )
    }

    // Auto-lock / manual lock / wipe -> jump to the lock (or setup) screen and clear the back stack,
    // so no screen with secrets stays in memory.
    LaunchedEffect(unlocked) {
        if (!unlocked) SecretGate.reset()
        val route = nav.currentDestination?.route ?: return@LaunchedEffect
        if (!unlocked && route != Routes.UNLOCK && route != Routes.SETUP) {
            iconLoader.clearWebIcons()
            nav.navigate(if (keyManager.vaultExists()) Routes.UNLOCK else Routes.SETUP) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        } else if (unlocked && route == Routes.UNLOCK) {
            nav.navigate(Routes.VAULT) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalSecretGate provides gate) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                NavHost(
                    navController = nav,
                    startDestination = start,
                    enterTransition = { if (reduceMotion) EnterTransition.None else pushEnter() },
                    exitTransition = { if (reduceMotion) ExitTransition.None else pushExit() },
                    popEnterTransition = { if (reduceMotion) EnterTransition.None else popEnter() },
                    popExitTransition = { if (reduceMotion) ExitTransition.None else popExit() },
                ) {
                    screen(
                        Routes.SETUP,
                        enter = { fadeIn() },
                        exit = { fadeOut() },
                    ) {
                        SetupScreen(
                            onFinished = {
                                nav.navigate(Routes.VAULT) { popUpTo(nav.graph.id) { inclusive = true } }
                            },
                            onRestore = { nav.navigate(Routes.backup(setup = true)) },
                        )
                    }
                    screen(
                        Routes.UNLOCK,
                        enter = { fadeIn(Motion.smooth()) },
                        exit = { fadeOut(Motion.smooth()) + scaleOut(Motion.smooth(), targetScale = 1.06f) },
                    ) {
                        UnlockScreen()
                    }
                    screen(
                        Routes.VAULT,
                        enter = {
                            val from = initialState.destination.route
                            if (from == Routes.UNLOCK || from == Routes.SETUP) fadeIn(Motion.smooth()) + scaleIn(Motion.smooth(), initialScale = 0.94f)
                            else pushEnter()
                        },
                    ) {
                        VaultListScreen(
                            onOpen = { nav.navigate(Routes.detail(it)) },
                            onEdit = { nav.navigate(Routes.edit(it)) },
                            onAdd = { nav.navigate(Routes.add(it.name)) },
                            onGenerator = { nav.navigate(Routes.GENERATOR) },
                            onSettings = { nav.navigate(Routes.SETTINGS) },
                            onBackup = { nav.navigate(Routes.backup()) },
                            onSecurity = { nav.navigate(Routes.SECURITY) },
                        )
                    }
                    screen(
                        Routes.DETAIL,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) {
                        EntryDetailScreen(
                            onBack = { nav.safePop() },
                            onEdit = { nav.navigate(Routes.edit(it)) },
                        )
                    }
                    screen(
                        Routes.EDIT,
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("type") { type = NavType.StringType; defaultValue = "LOGIN" },
                        ),
                        // Editor rises from the bottom like a sheet
                        enter = { slideInVertically(Motion.offset) { it } + fadeIn() },
                        popExit = { slideOutVertically(Motion.offset) { it } + fadeOut() },
                    ) { entry ->
                        // The QR scanner hands its result back through this entry's SavedStateHandle
                        val scanResult by entry.savedStateHandle
                            .getStateFlow<String?>(SCAN_RESULT, null)
                            .collectAsStateWithLifecycle()
                        EntryEditScreen(
                            onDone = { id, isNew ->
                                if (isNew) {
                                    nav.navigate(Routes.detail(id)) { popUpTo(Routes.VAULT) }
                                } else {
                                    nav.safePop()
                                }
                            },
                            onCancel = { nav.safePop() },
                            onScan = { nav.navigate(Routes.SCAN) },
                            scanResult = scanResult,
                            onScanConsumed = { entry.savedStateHandle[SCAN_RESULT] = null },
                        )
                    }
                    screen(
                        Routes.SCAN,
                        enter = { slideInVertically(Motion.offset) { it } + fadeIn() },
                        popExit = { slideOutVertically(Motion.offset) { it } + fadeOut() },
                    ) {
                        ScanScreen(
                            title = "Scan QR code",
                            hint = "Point at a 2FA setup QR code or a Wi-Fi QR code",
                            accept = { it.startsWith("otpauth://", true) || it.startsWith("WIFI:", true) },
                            onResult = { text ->
                                nav.previousBackStackEntry?.savedStateHandle?.set(SCAN_RESULT, text)
                                nav.safePop()
                            },
                            onBack = { nav.safePop() },
                        )
                    }
                    screen(Routes.GENERATOR) { GeneratorScreen(onBack = { nav.safePop() }) }
                    screen(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { nav.safePop() },
                            onBackup = { nav.navigate(Routes.backup()) },
                            onSecurity = { nav.navigate(Routes.SECURITY) },
                            onImport = { nav.navigate(Routes.IMPORT) },
                            onKit = { nav.navigate(Routes.KIT) },
                        )
                    }
                    screen(Routes.KIT) { EmergencyKitScreen(onBack = { nav.safePop() }) }
                    screen(Routes.SECURITY) {
                        SecurityScreen(onBack = { nav.safePop() }, onOpen = { nav.navigate(Routes.detail(it)) })
                    }
                    screen(Routes.IMPORT) {
                        ImportScreen(
                            onBack = { nav.safePop() },
                            onDone = { nav.popBackStack(Routes.VAULT, inclusive = false) },
                        )
                    }
                    screen(
                        Routes.BACKUP,
                        arguments = listOf(navArgument("setup") { type = NavType.BoolType; defaultValue = false }),
                    ) {
                        BackupScreen(
                            onBack = { nav.safePop() },
                            // New phone: vault was just created from the backup -> straight into it
                            onRestoredNewVault = {
                                nav.navigate(Routes.VAULT) { popUpTo(nav.graph.id) { inclusive = true } }
                            },
                        )
                    }
                }

                CopyPill(
                    state = clipState,
                    onClearNow = clipboard::clearNow,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
                )
            }
        }
    }
}

/** composable() that also exposes its AnimatedVisibilityScope for shared-element transitions. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    enter: (NavScope.() -> EnterTransition?)? = null,
    exit: (NavScope.() -> ExitTransition?)? = null,
    popExit: (NavScope.() -> ExitTransition?)? = null,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = enter,
        exitTransition = exit,
        // Explicit nulls: otherwise these default to enter/exit, and going *back* would replay the push animation
        popEnterTransition = null,
        popExitTransition = popExit,
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            content(entry)
        }
    }
}

/** Ignores double-taps on Back while a transition is running (prevents popping to a blank screen). */
private fun NavController.safePop() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED && previousBackStackEntry != null) {
        popBackStack()
    }
}
