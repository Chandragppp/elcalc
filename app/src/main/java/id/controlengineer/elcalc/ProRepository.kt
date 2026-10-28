package id.controlengineer.elcalc

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import id.controlengineer.elcalc.core.ProEntitlement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

internal val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(name = "elcalc_prefs")

/** Kontrak status & aksi Pro untuk produksi (Play Billing + AdMob + DataStore) maupun tes (Fake). */
interface ProRepository {
  val lifetime: StateFlow<Boolean>
  val rewardedUntilMillis: StateFlow<Long>
  val trialStartMillis: StateFlow<Long>
  val trialWelcomeShown: StateFlow<Boolean>
  val formattedPrice: StateFlow<String?>
  val statusMessage: StateFlow<String?>
  val privacyOptionsRequired: StateFlow<Boolean>
  fun purchase(activity: Activity)
  fun showRewarded(activity: Activity)
  fun refresh()
  fun dismissTrialWelcome() {}
  fun initializeConsentAndAds(activity: Activity) {}
  fun showPrivacyOptions(activity: Activity) {}
  fun clearStatusMessage() {}
}

/** Implementasi palsu untuk androidTest dan mode debug extra "pro" / "trial". */
class FakeProRepository(
  initialLifetime: Boolean = false,
  initialRewardedUntilMillis: Long = 0L,
  initialTrialStartMillis: Long = 0L,
  initialTrialWelcomeShown: Boolean = true,
  initialFormattedPrice: String? = null,
  private val simulateAdUnavailableMsg: String = "Rewarded ad unavailable in test mode.",
) : ProRepository {
  override val lifetime = MutableStateFlow(initialLifetime)
  override val rewardedUntilMillis = MutableStateFlow(initialRewardedUntilMillis)
  override val trialStartMillis = MutableStateFlow(initialTrialStartMillis)
  override val trialWelcomeShown = MutableStateFlow(initialTrialWelcomeShown)
  override val formattedPrice = MutableStateFlow(initialFormattedPrice)
  override val statusMessage = MutableStateFlow<String?>(null)
  override val privacyOptionsRequired = MutableStateFlow(false)

  override fun purchase(activity: Activity) {
    if (formattedPrice.value != null) lifetime.value = true else statusMessage.value = activity.getString(R.string.billing_unavailable_reason)
  }
  override fun showRewarded(activity: Activity) {
    statusMessage.value = activity.getString(R.string.ad_unavailable).ifBlank { simulateAdUnavailableMsg }
  }
  override fun refresh() {
    rewardedUntilMillis.value = ProEntitlement.clampRewardedUntil(rewardedUntilMillis.value, System.currentTimeMillis())
  }
  override fun dismissTrialWelcome() { trialWelcomeShown.value = true }
  override fun clearStatusMessage() { statusMessage.value = null }
}

/** Implementasi nyata: Google Play Billing (`pro_lifetime`), UMP + AdMob Rewarded, dan DataStore Preferences. */
class PlayProRepository(context: Context) : ProRepository {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  override val lifetime = MutableStateFlow(false)
  override val rewardedUntilMillis = MutableStateFlow(0L)
  override val trialStartMillis = MutableStateFlow(0L)
  override val trialWelcomeShown = MutableStateFlow(true)
  override val formattedPrice = MutableStateFlow<String?>(null)
  override val statusMessage = MutableStateFlow<String?>(null)
  override val privacyOptionsRequired = MutableStateFlow(false)

  private var productDetails: ProductDetails? = null
  private var rewardedAd: RewardedAd? = null
  private var isAdLoading = false
  private val adsInitialized = AtomicBoolean(false)

  private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
    when (result.responseCode) {
      BillingClient.BillingResponseCode.OK -> if (purchases != null) processPurchases(purchases, false)
      BillingClient.BillingResponseCode.USER_CANCELED -> Unit
      else -> statusMessage.value = appContext.getString(R.string.billing_unavailable_reason)
    }
  }

  private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
    .setListener(purchasesUpdatedListener)
    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
    .build()

  init {
    scope.launch {
      appContext.proDataStore.data.collect { prefs ->
        lifetime.value = prefs[KEY_LIFETIME] ?: false
        val now = System.currentTimeMillis()
        val storedStart = prefs[KEY_TRIAL_START]
        trialStartMillis.value = storedStart ?: now
        trialWelcomeShown.value = prefs[KEY_WELCOME_SHOWN] ?: false
        if (storedStart == null) appContext.proDataStore.edit { if (it[KEY_TRIAL_START] == null) it[KEY_TRIAL_START] = now }
        val rawUntil = prefs[KEY_REWARDED_UNTIL] ?: 0L
        val clamped = ProEntitlement.clampRewardedUntil(rawUntil, now)
        rewardedUntilMillis.value = clamped
        if (rawUntil != clamped && rawUntil > 0L) appContext.proDataStore.edit { it[KEY_REWARDED_UNTIL] = clamped }
      }
    }
    refresh()
  }

  override fun refresh() {
    val now = System.currentTimeMillis()
    val prev = rewardedUntilMillis.value
    val clamped = ProEntitlement.clampRewardedUntil(prev, now)
    rewardedUntilMillis.value = clamped
    if (clamped != prev && prev > 0L) scope.launch { appContext.proDataStore.edit { it[KEY_REWARDED_UNTIL] = clamped } }
    if (billingClient.isReady) queryProductAndPurchases() else runCatching {
      billingClient.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(r: BillingResult) { if (r.responseCode == BillingClient.BillingResponseCode.OK) queryProductAndPurchases() }
        override fun onBillingServiceDisconnected() = Unit
      })
    }
  }

  private fun queryProductAndPurchases() {
    val prod = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
    billingClient.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(prod)).build()) { r, details ->
      if (r.responseCode == BillingClient.BillingResponseCode.OK) {
        val found = details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
        productDetails = found
        formattedPrice.value = found?.oneTimePurchaseOfferDetails?.formattedPrice
      }
    }
    billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
      if (r.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(list, true)
    }
  }

  private fun processPurchases(purchases: List<Purchase>, fromFullQuery: Boolean) {
    var bought = false; var pending = false
    for (p in purchases) {
      if (!p.products.contains(PRODUCT_ID)) continue
      when (p.purchaseState) {
        Purchase.PurchaseState.PURCHASED -> {
          bought = true
          if (!p.isAcknowledged) billingClient.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { _ -> }
        }
        Purchase.PurchaseState.PENDING -> pending = true
        else -> Unit
      }
    }
    if (pending && !bought) statusMessage.value = appContext.getString(R.string.purchase_pending)
    if (fromFullQuery || bought) {
      lifetime.value = bought
      scope.launch { appContext.proDataStore.edit { it[KEY_LIFETIME] = bought } }
    }
  }

  override fun purchase(activity: Activity) {
    val d = productDetails
    if (d == null || !billingClient.isReady) { statusMessage.value = appContext.getString(R.string.billing_unavailable_reason); refresh(); return }
    val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
    billingClient.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).build())
  }

  override fun initializeConsentAndAds(activity: Activity) {
    val ci = UserMessagingPlatform.getConsentInformation(activity)
    val done = {
      privacyOptionsRequired.value = ci.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
      if (ci.canRequestAds()) ensureAdsInitializedAndPreload()
    }
    ci.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> done() } }, { _ -> done() })
  }

  override fun showPrivacyOptions(activity: Activity) {
    UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
      val ci = UserMessagingPlatform.getConsentInformation(activity)
      privacyOptionsRequired.value = ci.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
      if (ci.canRequestAds()) ensureAdsInitializedAndPreload()
    }
  }

  private fun ensureAdsInitializedAndPreload() {
    if (adsInitialized.compareAndSet(false, true)) MobileAds.initialize(appContext) { scope.launch { loadRewardedAd() } } else loadRewardedAd()
  }

  private fun loadRewardedAd() {
    if (isAdLoading || rewardedAd != null || !UserMessagingPlatform.getConsentInformation(appContext).canRequestAds()) return
    isAdLoading = true
    RewardedAd.load(appContext, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
      override fun onAdLoaded(ad: RewardedAd) { isAdLoading = false; rewardedAd = ad }
      override fun onAdFailedToLoad(err: LoadAdError) { isAdLoading = false; rewardedAd = null }
    })
  }

  override fun showRewarded(activity: Activity) {
    clearStatusMessage()
    val ad = rewardedAd
    if (ad == null) {
      statusMessage.value = appContext.getString(R.string.ad_unavailable)
      if (UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) ensureAdsInitializedAndPreload() else initializeConsentAndAds(activity)
      return
    }
    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
      override fun onAdDismissedFullScreenContent() { rewardedAd = null; loadRewardedAd() }
      override fun onAdFailedToShowFullScreenContent(err: AdError) { rewardedAd = null; statusMessage.value = appContext.getString(R.string.ad_unavailable); loadRewardedAd() }
    }
    ad.show(activity) { _ ->
      val until = System.currentTimeMillis() + ProEntitlement.REWARD_DURATION_MS
      rewardedUntilMillis.value = until
      scope.launch { appContext.proDataStore.edit { it[KEY_REWARDED_UNTIL] = until } }
    }
  }

  override fun dismissTrialWelcome() {
    trialWelcomeShown.value = true
    scope.launch { appContext.proDataStore.edit { it[KEY_WELCOME_SHOWN] = true } }
  }

  internal fun resetFirstOpenForTest() {
    val now = System.currentTimeMillis()
    trialStartMillis.value = now; trialWelcomeShown.value = false
    scope.launch { appContext.proDataStore.edit { it[KEY_TRIAL_START] = now; it[KEY_WELCOME_SHOWN] = false } }
  }

  override fun clearStatusMessage() { statusMessage.value = null }

  companion object {
    const val PRODUCT_ID = "pro_lifetime"
    private val KEY_LIFETIME = booleanPreferencesKey("pro_lifetime")
    private val KEY_REWARDED_UNTIL = longPreferencesKey("rewarded_until_millis")
    private val KEY_TRIAL_START = longPreferencesKey("trial_start_millis")
    private val KEY_WELCOME_SHOWN = booleanPreferencesKey("trial_welcome_shown")
  }
}
