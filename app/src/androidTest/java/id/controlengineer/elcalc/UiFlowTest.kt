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
    // Tabel KHA tersimpan di DataStore: kosongkan agar tiap tes mulai bersih
    kotlinx.coroutines.runBlocking { KhaTableStore(rule.activity).clearAll() }
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

    // Default tabel bawaan IEC B.52.4 Cu PVC B1 (3 konduktor, 30 °C, 1 sirkit): 6 mm² = 36 A (kurang 14 A), 10 mm² = 50 A (pas),
    // 16 mm² = 68 A. ΔV 10 mm² = 2,12 % -> rekomendasi 10 mm² (ΔV saja akan memberi 6 mm²)
    rule.onNodeWithTag("cable_rec_text").performScrollTo().assertTextContains("10 mm²", substring = true)
    rule.onNodeWithTag("cable_opt_smaller").performScrollTo().assertTextContains("36.0 A", substring = true)
    rule.onNodeWithTag("cable_opt_larger").performScrollTo().assertTextContains("68.0 A", substring = true)
    rule.onNodeWithTag("cable_kha_warning").assertDoesNotExist()
    shot("cable-recommendation")

    // DC kontrol 24 V, 2 A, 50 m: 4 mm² (4,69 %)
    click("chip:" + str(R.string.cable_circuit_dc))
    type("cable_voltage_input", "24")
    type("cable_load_input", "2")
    rule.onNodeWithTag("cable_cosphi_input").assertDoesNotExist()
    rule.onNodeWithTag("cable_rec_text").performScrollTo().assertTextContains("4 mm²", substring = true)
    rule.onNodeWithTag("cable_opt_rec").performScrollTo().assertTextContains("4.69 %", substring = true)
    shot("cable-dc-24v")
  }

  // 2b. Tabel KHA buatan pengguna: kabel 5 m 50 A naik dari 1,5 mm² (ΔV saja) ke 16 mm² (KHA 61 A)
  @Test
  fun cableKhaTableDrivesRecommendation() {
    launchProMode("off", tab = 1)
    click("chip:" + str(R.string.phase_1p))
    type("cable_voltage_input", "220")
    type("cable_length_input", "5")
    type("cable_load_input", "50")
    type("cable_cosphi_input", "0.9")
    type("cable_limit_input", "4")
    // IEC B.52.2 Cu PVC B1 (2 konduktor): 6 mm² = 41 A, 10 mm² = 57 A -> 10 mm²
    rule.onNodeWithTag("cable_rec_text").performScrollTo().assertTextContains("10 mm²", substring = true)
    // A1 (sama dengan app pembanding): 10 mm² = 46 A kurang 4 A, 16 mm² = 61 A sisa 11 A
    click("chip:A1")
    rule.onNodeWithTag("cable_rec_text").performScrollTo().assertTextContains("16 mm²", substring = true)
    rule.onNodeWithTag("cable_opt_smaller").performScrollTo().assertTextContains("46.0 A", substring = true)
    rule.onNodeWithTag("cable_opt_rec").performScrollTo().assertTextContains("11.0 A", substring = true)
    shot("cable-kha-16mm")
    // 40 °C (k_t 0,87) & 2 sirkit digabung (k_g 0,80): 16 mm² = 42,46 A gagal, 25 mm² = 80 × 0,696 = 55,68 A -> 25 mm²
    type("cable_ambient_input", "40")
    type("cable_circuits_input", "2")
    rule.onNodeWithTag("cable_factors_text").performScrollTo().assertTextContains("k_t = 0.87 · k_g = 0.8", substring = true)
    rule.onNodeWithTag("cable_rec_text").performScrollTo().assertTextContains("25 mm²", substring = true)

    // Tabel milik pengguna menggantikan tabel bawaan
    click("btn_kha_new")
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("kha_dialog").fetchSemanticsNodes().isNotEmpty() }
    type("kha_name_input", "Tabel saya")
    type("kha_field_10", "55")
    type("kha_field_16", "75")
    shot("kha-table-editor")
    rule.onNodeWithTag("btn_kha_save").performClick()
    rule.waitUntil(5_000) {
      rule.onAllNodes(hasTestTag("cable_rec_text") and hasText("10 mm²", substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag("cable_opt_rec").performScrollTo().assertTextContains("55.0 A", substring = true)
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
    rule.onNodeWithTag("paywall_one_time_text").assertTextContains(str(R.string.paywall_one_time_badge), substring = true)
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
