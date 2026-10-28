package id.controlengineer.elcalc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.controlengineer.elcalc.core.ProEntitlement
import id.controlengineer.elcalc.core.ProLabelSource
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
  private var startTabState by mutableIntStateOf(0)
  private var startSubTabState by mutableIntStateOf(0)
  private var startPaywallState by mutableStateOf(false)
  private var intentGeneration by mutableIntStateOf(0)
  private var playRepo: PlayProRepository? = null
  internal var activeRepo by mutableStateOf<ProRepository?>(null)
    private set

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    applyIntent(intent)
    setContent {
      val repo = activeRepo ?: return@setContent
      ElCalcTheme { ElCalcApp(repo, startTabState, startSubTabState, startPaywallState, intentGeneration) }
    }
  }

  override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); applyIntent(intent) }
  override fun onResume() { super.onResume(); activeRepo?.refresh() }

  internal fun applyIntent(intent: Intent?) {
    // Extra intent "pro"/"tab"/"subtab"/"trial"/"paywall"/"welcome" HANYA dibaca pada build debug.
    val debugPro = if (BuildConfig.DEBUG) intent?.getStringExtra("pro")?.lowercase() else null
    startTabState = if (BuildConfig.DEBUG) (intent?.getIntExtra("tab", 0) ?: 0).coerceIn(0, 4) else 0
    startSubTabState = if (BuildConfig.DEBUG) (intent?.getIntExtra("subtab", 0) ?: 0).coerceIn(0, 2) else 0
    val debugTrialRaw = if (BuildConfig.DEBUG) (intent?.getStringExtra("trial") ?: intent?.extras?.get("trial")?.toString())?.trim()?.lowercase() else null
    startPaywallState = BuildConfig.DEBUG && intent?.getBooleanExtra("paywall", false) == true
    val debugShowWelcome = BuildConfig.DEBUG && intent?.getBooleanExtra("welcome", false) == true
    val rewardedMs = if (BuildConfig.DEBUG) intent?.getLongExtra("rewarded_ms", -1L) ?: -1L else -1L
    intentGeneration++

    val now = System.currentTimeMillis()
    activeRepo = when {
      debugPro == "on" -> FakeProRepository(initialLifetime = true, initialFormattedPrice = "Rp 39.000")
      debugPro == "off" -> FakeProRepository(initialLifetime = false, initialFormattedPrice = "Rp 39.000")
      debugTrialRaw != null -> {
        val h = debugTrialRaw.toIntOrNull()
        val start = when {
          debugTrialRaw == "fresh" -> now
          debugTrialRaw == "expired" || (h != null && h <= 0) -> now - ProEntitlement.TRIAL_DURATION_MS - 3_600_000L
          h != null -> now - ProEntitlement.TRIAL_DURATION_MS + h * 3_600_000L - 60_000L
          else -> now
        }
        FakeProRepository(false, 0L, start, !debugShowWelcome, "Rp 39.000")
      }
      rewardedMs > 0L -> FakeProRepository(false, now + rewardedMs, 0L, true, "Rp 39.000")
      debugShowWelcome -> FakeProRepository(false, 0L, now, false, "Rp 39.000")
      else -> (playRepo ?: PlayProRepository(applicationContext).also { playRepo = it }).also {
        if (debugShowWelcome) it.resetFirstOpenForTest()
        it.initializeConsentAndAds(this)
      }
    }
  }
}

@Composable
fun ElCalcTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(), content = content)
}

private enum class Tab(val labelRes: Int, val tag: String, val iconText: String, val isProOnly: Boolean) {
  BASIC(R.string.tab_basic, "nav_basic", "Ω", false),
  CABLE(R.string.tab_cable, "nav_cable", "ΔV", false),
  MOTOR(R.string.tab_motor, "nav_motor", "M3~", true),
  CAP_BANK(R.string.tab_cap, "nav_cap", "kVAR", true),
  UNBALANCE(R.string.tab_unbalance, "nav_unbalance", "3~%", true),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElCalcApp(repo: ProRepository, initialTab: Int = 0, initialSubtab: Int = 0, initialOpenPaywall: Boolean = false, intentKey: Int = 0) {
  var tab by rememberSaveable(intentKey) { mutableIntStateOf(initialTab) }
  var showPaywall by rememberSaveable(intentKey) { mutableStateOf(initialOpenPaywall) }
  var showAbout by rememberSaveable(intentKey) { mutableStateOf(false) }

  val lifetime by repo.lifetime.collectAsState()
  val rewardedUntilMillis by repo.rewardedUntilMillis.collectAsState()
  val trialStartMillis by repo.trialStartMillis.collectAsState()
  val trialWelcomeShown by repo.trialWelcomeShown.collectAsState()
  val formattedPrice by repo.formattedPrice.collectAsState()
  val statusMessage by repo.statusMessage.collectAsState()
  val privacyOptionsRequired by repo.privacyOptionsRequired.collectAsState()

  var nowMillis by remember(lifetime, rewardedUntilMillis, trialStartMillis) { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(lifetime, rewardedUntilMillis, trialStartMillis) {
    while (true) {
      val current = System.currentTimeMillis(); nowMillis = current
      val nextExpiry = ProEntitlement.nextExpiryMillis(lifetime, rewardedUntilMillis, trialStartMillis, current)
      if (nextExpiry <= current) break
      delay((nextExpiry - current + 50L).coerceAtMost(60_000L))
      repo.refresh(); nowMillis = System.currentTimeMillis()
    }
  }

  val isPro = ProEntitlement.isProActive(lifetime, rewardedUntilMillis, nowMillis, trialStartMillis)
  val inTrial = ProEntitlement.isTrialActive(trialStartMillis, nowMillis)
  val trialEnded = !lifetime && ProEntitlement.isTrialExpired(trialStartMillis, nowMillis) && ProEntitlement.clampRewardedUntil(rewardedUntilMillis, nowMillis) <= nowMillis
  val labelSource = ProEntitlement.labelSource(lifetime, rewardedUntilMillis, trialStartMillis, nowMillis)
  val activity = LocalContext.current as? Activity

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(s(R.string.app_name)) },
        actions = {
          TextButton({ showAbout = true }, Modifier.testTag("btn_about")) { Text(s(R.string.about)) }
          if (privacyOptionsRequired && activity != null) {
            TextButton({ repo.showPrivacyOptions(activity) }, Modifier.testTag("btn_privacy_options")) { Text(s(R.string.privacy_options)) }
          }
          val statusLabel = when (labelSource) {
            ProLabelSource.LIFETIME -> s(R.string.pro_lifetime)
            ProLabelSource.REWARDED -> {
              val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
              stringResource(R.string.pro_active_until, fmt.format(Date(ProEntitlement.clampRewardedUntil(rewardedUntilMillis, nowMillis))))
            }
            ProLabelSource.TRIAL -> {
              val rem = ProEntitlement.trialRemainingMillis(trialStartMillis, nowMillis)
              val d = ProEntitlement.trialDaysLeft(rem)
              if (d >= 2) stringResource(R.string.pro_trial_days, d) else stringResource(R.string.pro_trial_hours, ProEntitlement.trialHoursLeft(rem))
            }
            ProLabelSource.NONE -> s(R.string.pro_unlock)
          }
          AssistChip({ if (!lifetime) { repo.clearStatusMessage(); showPaywall = true } }, { Text(statusLabel) }, Modifier.padding(end = 12.dp).testTag("pro_status_chip"))
        },
      )
    },
    bottomBar = {
      NavigationBar {
        Tab.entries.forEachIndexed { idx, t ->
          NavigationBarItem(
            selected = tab == idx,
            onClick = { tab = idx },
            icon = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.iconText, fontFamily = FontFamily.Monospace)
                if (t.isProOnly && !isPro) Text(" PRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              }
            },
            label = { Text(s(t.labelRes)) },
            modifier = Modifier.testTag(t.tag),
          )
        }
      }
    },
  ) { innerPadding ->
    val unlockAction = { repo.clearStatusMessage(); showPaywall = true }
    Box(Modifier.fillMaxSize().padding(innerPadding)) {
      when (Tab.entries[tab]) {
        Tab.BASIC -> BasicScreen(initialSubTab = if (tab == initialTab) initialSubtab else 0)
        Tab.CABLE -> CableScreen()
        Tab.MOTOR -> if (isPro) MotorScreen(initialSubtab = if (tab == initialTab) initialSubtab else 0) else LockedProTabScreen(R.string.motor_title, unlockAction)
        Tab.CAP_BANK -> if (isPro) CapacitorBankScreen() else LockedProTabScreen(R.string.cap_title, unlockAction)
        Tab.UNBALANCE -> if (isPro) PhaseUnbalanceScreen() else LockedProTabScreen(R.string.unb_title, unlockAction)
      }
    }
  }

  if (showAbout) AboutDialog(onDismiss = { showAbout = false })

  if (!trialWelcomeShown && inTrial && !showPaywall && !showAbout) {
    AlertDialog(
      onDismissRequest = { repo.dismissTrialWelcome() },
      title = { Text(s(R.string.trial_welcome_title)) },
      text = { Text(s(R.string.trial_welcome_message)) },
      confirmButton = { TextButton({ repo.dismissTrialWelcome() }, Modifier.testTag("btn_welcome_ok")) { Text(s(android.R.string.ok)) } },
      modifier = Modifier.testTag("trial_welcome_dialog"),
    )
  }

  if (showPaywall) {
    PaywallBottomSheet(
      price = formattedPrice, trialEnded = trialEnded, statusMessage = statusMessage,
      onWatchAd = { if (activity != null) repo.showRewarded(activity) },
      onBuyLifetime = { if (activity != null) repo.purchase(activity) },
      onDismiss = { repo.clearStatusMessage(); showPaywall = false },
    )
  }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(s(R.string.about_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge)
        Text(s(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
          Text(s(R.string.independent_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(10.dp).testTag("about_disclaimer_text"))
        }
      }
    },
    confirmButton = { TextButton(onDismiss, Modifier.testTag("btn_about_close")) { Text(s(R.string.close)) } },
    modifier = Modifier.testTag("about_dialog"),
  )
}

@Composable
private fun LockedProTabScreen(titleRes: Int, onUnlock: () -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("locked_pro_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(s(titleRes), style = MaterialTheme.typography.headlineSmall)
    Text(s(R.string.paywall_one_time_badge), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    Text(s(R.string.paywall_subtitle), style = MaterialTheme.typography.bodyMedium)
    Card(Modifier.fillMaxWidth()) { Text(s(R.string.paywall_features), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
    Button(onUnlock, Modifier.fillMaxWidth().testTag("btn_unlock_pro")) { Text(s(R.string.pro_unlock)) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaywallBottomSheet(price: String?, trialEnded: Boolean, statusMessage: String?, onWatchAd: () -> Unit, onBuyLifetime: () -> Unit, onDismiss: () -> Unit) {
  ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("paywall_sheet")) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (trialEnded) AssistChip({}, { Text(s(R.string.paywall_trial_ended)) }, Modifier.testTag("badge_trial_ended"))
      Text(s(R.string.paywall_title), style = MaterialTheme.typography.headlineSmall)
      Text(s(R.string.paywall_one_time_badge), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("paywall_one_time_text"))
      Text(s(R.string.paywall_subtitle), style = MaterialTheme.typography.bodyMedium)
      Text(s(R.string.paywall_features), style = MaterialTheme.typography.bodyMedium)
      val buyLabel = if (price != null) stringResource(R.string.paywall_buy_price, price) else s(R.string.paywall_buy_unavailable)
      Button(onBuyLifetime, Modifier.fillMaxWidth().testTag("btn_buy_pro"), enabled = price != null) { Text(buyLabel) }
      if (price == null) Text(s(R.string.billing_unavailable_reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("billing_unavailable_text"))
      Text(s(R.string.paywall_reward_disclosure), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("reward_disclosure_text"))
      OutlinedButton(onWatchAd, Modifier.fillMaxWidth().testTag("btn_watch_ad")) { Text(s(R.string.paywall_watch_ad)) }
      if (statusMessage != null) Text(statusMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      TextButton(onDismiss, Modifier.align(Alignment.End).testTag("paywall_close")) { Text(s(R.string.close)) }
      Spacer(Modifier.height(8.dp))
    }
  }
}
