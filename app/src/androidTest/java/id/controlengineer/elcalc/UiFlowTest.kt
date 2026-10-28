package id.controlengineer.elcalc

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Alur UI utama di emulator. Tiap tes menyimpan screenshot ke filesDir/shots (diambil skrip CI lewat run-as). */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
  @get:Rule
  val rule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setUp() {
    launchProMode("off", tab = 0)
  }

  private fun str(id: Int) = rule.activity.getString(id)

  private fun value(label: String): SemanticsNodeInteraction =
    rule.onNodeWithTag("value:$label", useUnmergedTree = true).performScrollTo()

  private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()

  private fun type(tag: String, text: String) {
    rule.onNodeWithTag(tag).performScrollTo().performTextClearance()
    rule.onNodeWithTag(tag).performTextInput(text)
  }

  private fun launchProMode(pro: String, tab: Int = 0, subtab: Int = 0) {
    rule.runOnUiThread {
      val intent = Intent(rule.activity, MainActivity::class.java)
        .putExtra("pro", pro).putExtra("tab", tab).putExtra("subtab", subtab)
      rule.activity.applyIntent(intent)
    }
    rule.waitForIdle()
  }

  private fun shot(name: String) {
    rule.waitForIdle()
    val target = if (rule.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) rule.onNode(isDialog()) else rule.onRoot()
    val bmp = try {
      target.captureToImage().asAndroidBitmap()
    } catch (e: AssertionError) {
      android.util.Log.w("UiFlowTest", "screenshot $name gagal: ${e.message}")
      return
    }
    val dir = File(rule.activity.filesDir, "shots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  // 1. Daya 3 fasa menghitung arus
  @Test
  fun acPower3PhaseCalculatesCurrent() {
    launchProMode("off", tab = 0, subtab = 1)
    click("chip:" + str(R.string.acp_mode_kw))
    type("acp_voltage_input", "400")
    type("acp_value_input", "15")
    type("acp_cosphi_input", "0.85")

    value(str(R.string.acp_out_current)).assertTextContains("25.47 A", substring = true)
    value(str(R.string.acp_out_s)).assertTextContains("17.65 kVA", substring = true)
    shot("basic-3p-power")
  }

  // 2. Susut tegangan kabel menampilkan % dan status
  @Test
  fun cableVoltageDropShowsPercentAndStatus() {
    launchProMode("off", tab = 1)
    type("cable_length_input", "50")
    type("cable_voltage_input", "400")
    type("cable_load_input", "50")
    type("cable_cosphi_input", "0.85")
    type("cable_limit_input", "5")

    value(str(R.string.cable_out_dv_pct)).assertTextContains("1.34 %", substring = true)
    rule.onNodeWithTag("cable_status_text").performScrollTo().assertTextContains("OK", substring = true)
    shot("cable-voltage-drop")
  }

  // 3. Tab Motor terkunci saat pro=off dan membuka paywall
  @Test
  fun motorTabLockedWhenProOffAndOpensPaywall() {
    launchProMode("off", tab = 2)
    rule.onNodeWithTag("locked_pro_screen").assertIsDisplayed()
    rule.onNodeWithTag("motor_power_input").assertDoesNotExist()
    shot("motor-locked")

    click("btn_unlock_pro")
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("paywall_sheet").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("paywall_sheet").assertIsDisplayed()
    rule.onNodeWithTag("paywall_one_time_text").assertTextContains("Bayar sekali", substring = true)
    rule.onNodeWithTag("btn_buy_pro").assertIsDisplayed()
    rule.onNodeWithTag("btn_watch_ad").performScrollTo().assertIsDisplayed()
    shot("motor-paywall")
  }

  // 4. pro=on: Star-Delta 7,5 kW menampilkan arus kontaktor
  @Test
  fun proOnMotorStarDelta7_5kWShowsContactorCurrents() {
    launchProMode("on", tab = 2, subtab = 1)
    type("motor_power_input", "7.5")
    type("motor_voltage_input", "400")
    type("motor_cosphi_input", "0.85")
    type("motor_eta_input", "0.9")
    click("chip:" + str(R.string.motor_method_sd))

    value(str(R.string.motor_out_in)).assertTextContains("14.15 A", substring = true)
    value(str(R.string.motor_out_km)).assertTextContains("8.21 A", substring = true)
    value(str(R.string.motor_out_kdelta)).assertTextContains("8.21 A", substring = true)
    value(str(R.string.motor_out_kstar)).assertTextContains("4.67 A", substring = true)
    rule.onNodeWithTag("pro_disclaimer").performScrollTo().assertTextContains("SNI 0225:2020", substring = true)
    shot("motor-star-delta-7_5kw")
  }

  // 5. pro=on: Kapasitor bank menghitung kVAR dan penurunan arus
  @Test
  fun proOnCapacitorBank100kWCalculatesKvarAndCurrentReduction() {
    launchProMode("on", tab = 3)
    type("cap_p_input", "100")
    type("cap_v_input", "400")
    type("cap_cosphi1_input", "0.7")
    type("cap_cosphi2_input", "0.95")

    value(str(R.string.cap_out_q)).assertTextContains("69.15 kVAR", substring = true)
    value(str(R.string.cap_out_i_saved)).assertTextContains("54.26 A", substring = true)
    rule.onNodeWithTag("cap_step_6").performScrollTo().assertTextContains("12.5", substring = true)
    rule.onNodeWithTag("pro_disclaimer").performScrollTo().assertTextContains("SNI 0225:2020", substring = true)
    shot("capacitor-bank-100kw")
  }

  // 6. Tentang memuat penafian independen
  @Test
  fun aboutDialogShowsDisclaimer() {
    launchProMode("off", tab = 0)
    rule.onNodeWithTag("btn_about").performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("about_dialog").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("about_dialog").assertIsDisplayed()
    rule.onNodeWithTag("about_disclaimer_text").assertTextContains("SNI 0225:2020 (PUIL 2020) / IEC 60364", substring = true)
    shot("about-disclaimer")
  }
}
