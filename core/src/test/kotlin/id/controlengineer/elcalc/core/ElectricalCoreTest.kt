package id.controlengineer.elcalc.core

import kotlin.test.*

/** Unit test modul :core; semua vektor uji dihitung tangan dan langkahnya ditulis di komentar. */
class ElectricalCoreTest {

  // 1. Motor 3 Fasa: In motor 7,5 kW, 400 V, cos φ = 0,85, η = 0,90 & faktor Star-Delta
  @Test
  fun motor7_5kW400VCosPhi0_85Eta0_90AndStarDeltaFactors() {
    // Hitungan tangan: P=7500 W, V=400 V, cosPhi=0.85, eta=0.90 -> penyebut = sqrt(3)*400*0.85*0.90 = 530.0075471
    // In = 7500 / 530.0075471 = 14.150742 A. DOL (k=6): KM >= 14.1507 A (std 18 A), TOR = 14.1507 A, I_start = 84.904451 A,
    // proteksi 1.25..2.0 In = 17.69..28.30 A (std 20..32 A).
    // Star-Delta: KM & KDelta = 0.58 * 14.150742 = 8.207430 A (std 9 A), KStar = 0.33 * 14.150742 = 4.669745 A (std 9 A),
    // TOR fasa = 8.207430 A, I_start_Y = 84.904451 / 3 = 28.301484 A.
    val dol = (MotorCalculator.calculate(7.5, MotorPowerUnit.KW, 400.0, 0.85, 0.90, StarterMethod.DOL, 6.0) as MotorOutcome.Success).result
    assertEquals(14.150742, dol.nominalCurrentInA, 1e-5)
    assertEquals(14.150742, dol.mainContactorMinCurrentA, 1e-5)
    assertEquals(18, dol.mainContactorStdRatingA)
    assertNull(dol.deltaContactorMinCurrentA)
    assertNull(dol.starContactorMinCurrentA)
    assertEquals(14.150742, dol.torSettingCurrentA, 1e-5)
    assertEquals(84.904451, dol.estimatedActualStartCurrentA, 1e-5)
    assertEquals(20, dol.recommendedBreakerStdMinA)
    assertEquals(32, dol.recommendedBreakerStdMaxA)

    val sd = (MotorCalculator.calculate(7.5, MotorPowerUnit.KW, 400.0, 0.85, 0.90, StarterMethod.STAR_DELTA, 6.0) as MotorOutcome.Success).result
    assertEquals(14.150742, sd.nominalCurrentInA, 1e-5)
    assertEquals(8.207430, sd.mainContactorMinCurrentA, 1e-5)
    assertEquals(8.207430, sd.deltaContactorMinCurrentA!!, 1e-5)
    assertEquals(4.669745, sd.starContactorMinCurrentA!!, 1e-5)
    assertEquals(9, sd.mainContactorStdRatingA)
    assertEquals(9, sd.deltaContactorStdRatingA)
    assertEquals(9, sd.starContactorStdRatingA)
    assertEquals(8.207430, sd.torSettingCurrentA, 1e-5)
    assertEquals(28.301484, sd.estimatedActualStartCurrentA, 1e-5)
  }

  // 2. Konversi HP ke kW (1 HP = 745,7 W)
  @Test
  fun horsepowerToKilowattConversion() {
    // Hitungan tangan: 1 HP = 0.7457 kW; 10 HP = 7.457 kW -> In (400 V, 0.85, 0.90) = 7457 / 530.0075471 = 14.069611 A.
    assertEquals(0.7457, MotorCalculator.hpToKw(1.0), 1e-9)
    assertEquals(7.457, MotorCalculator.hpToKw(10.0), 1e-9)
    assertEquals(10.0, MotorCalculator.kwToHp(7.457), 1e-9)
    val hpRes = (MotorCalculator.calculate(10.0, MotorPowerUnit.HP, 400.0, 0.85, 0.90, StarterMethod.DOL) as MotorOutcome.Success).result
    assertEquals(7.457, hpRes.powerKw, 1e-6)
    assertEquals(14.069611, hpRes.nominalCurrentInA, 1e-5)
  }

  // 3. Rekomendasi penampang dari susut tegangan (3F, DC kontrol) + ukuran tetangga + KHA
  @Test
  fun cableRecommendationWithNeighboursDcAndKha() {
    // Hitungan tangan 3F (IEC 60364-5-52 Annex G): V=400 V, Cu (rho=0.0225), X=0.00008 Ohm/m, L=80 m, I=50 A, cosPhi=0.85 (sin=0.526783), batas 3 %
    // dV(S) = sqrt(3)*50*80*(0.0225/S*0.85 + 0.00008*0.526783)
    // 10 mm2: 13.542161 V = 3.385540 % (kurang 0.385540 %); 16 mm2: 8.573341 V = 2.143335 % (sisa 0.856665 %); 25 mm2: 5.592048 V = 1.398012 % (sisa 1.601988 %)
    // Rekomendasi 16 mm2 (tanpa tabel KHA).
    val r3 = (CableCalculator.calculate(
      CableCircuit.THREE_PHASE, ConductorMaterial.COPPER, ResistivityMethod.IEC_60364_ANNEX_G,
      80.0, 400.0, CableLoadInputMode.CURRENT_AMPS, 50.0, 0.85, 3.0,
    ) as CableOutcome.Success).result
    assertFalse(r3.khaChecked)
    assertEquals(16.0, r3.recommended!!.sectionMm2)
    assertEquals(1.40625, r3.recommended!!.resistancePerKmOhm, 1e-6)
    assertEquals(8.573341, r3.recommended!!.voltageDropV, 1e-5)
    assertEquals(2.143335, r3.recommended!!.voltageDropPercent, 1e-5)
    assertEquals(0.856665, r3.recommended!!.marginPercent, 1e-5)
    assertEquals(10.0, r3.smaller!!.sectionMm2)
    assertEquals(-0.385540, r3.smaller!!.marginPercent, 1e-5)
    assertFalse(r3.smaller!!.withinLimit)
    assertEquals(25.0, r3.larger!!.sectionMm2)
    assertEquals(1.601988, r3.larger!!.marginPercent, 1e-5)

    // Hitungan tangan DC kontrol: 24 V, 2 A, 50 m, Cu, batas 5 % (1.2 V); dV = 2*2*50*0.0225/S = 4.5/S V; cos φ diabaikan
    // 2.5 mm2: 1.8 V = 7.5 % (kurang 2.5 %); 4 mm2: 1.125 V = 4.6875 % (sisa 0.3125 %); 6 mm2: 0.75 V = 3.125 % (sisa 1.875 %)
    val dc = (CableCalculator.calculate(
      CableCircuit.DC, lengthM = 50.0, voltageV = 24.0, loadValue = 2.0, cosPhi = null, maxDropLimitPercent = 5.0,
    ) as CableOutcome.Success).result
    assertEquals(4.0, dc.recommended!!.sectionMm2)
    assertEquals(1.125, dc.recommended!!.voltageDropV, 1e-9)
    assertEquals(0.3125, dc.recommended!!.marginPercent, 1e-9)
    assertEquals(-2.5, dc.smaller!!.marginPercent, 1e-9)
    assertEquals(1.875, dc.larger!!.marginPercent, 1e-9)
    assertFalse(dc.khaChecked)

    // Beban 400 A, 500 m, 3F 400 V, batas 5 %: 300 mm2 pun 9.17 % -> tidak ada rekomendasi, pembanding = 300 mm2
    val big = (CableCalculator.calculate(CableCircuit.THREE_PHASE, lengthM = 500.0, voltageV = 400.0, loadValue = 400.0, cosPhi = 0.85, maxDropLimitPercent = 5.0) as CableOutcome.Success).result
    assertNull(big.recommended)
    assertEquals(300.0, big.smaller!!.sectionMm2)
    assertEquals(9.1706, big.smaller!!.voltageDropPercent, 1e-4)
  }

  // 3b. Tabel KHA pengguna: kabel pendek arus besar ditentukan KHA, bukan dV
  @Test
  fun cableKhaTableDrivesRecommendationForShortRun() {
    // Kasus dari pemilik (dibandingkan dengan app lain): 1F 220 V, 50 A, cos φ 0,9 (sin 0.435890), 5 m, batas 4 %, Cu.
    // dV(S) = 2*50*5*(0.0225/S*0.9 + 0.00008*0.435890); 1.5 mm2: 6.767436 V = 3.076107 % (lolos dV); 16 mm2: 0.650248 V = 0.295567 %.
    // Tabel KHA uji (diketik pengguna; nilai yang sama tampil di app pembanding: 16 mm2 = 61 A): 10 -> 46 A, 16 -> 61 A, 25 -> 80 A.
    // Tanpa tabel: 1.5 mm2. Dengan tabel (k = 1): 10 mm2 kurang 4 A, 16 mm2 sisa 11 A -> rekomendasi 16 mm2; 25 mm2 sisa 30 A.
    val noTable = (CableCalculator.calculate(
      CableCircuit.SINGLE_PHASE, lengthM = 5.0, voltageV = 220.0, loadValue = 50.0, cosPhi = 0.9, maxDropLimitPercent = 4.0,
    ) as CableOutcome.Success).result
    assertEquals(1.5, noTable.recommended!!.sectionMm2)
    assertEquals(3.076107, noTable.recommended!!.voltageDropPercent, 1e-5)

    val table = KhaTable("A1 PVC 2c", mapOf(1.5 to 14.5, 2.5 to 19.5, 4.0 to 26.0, 6.0 to 34.0, 10.0 to 46.0, 16.0 to 61.0, 25.0 to 80.0))
    val r = (CableCalculator.calculate(
      CableCircuit.SINGLE_PHASE, lengthM = 5.0, voltageV = 220.0, loadValue = 50.0, cosPhi = 0.9, maxDropLimitPercent = 4.0, khaTable = table,
    ) as CableOutcome.Success).result
    assertTrue(r.khaChecked)
    assertEquals(16.0, r.recommended!!.sectionMm2)
    assertEquals(0.295567, r.recommended!!.voltageDropPercent, 1e-5)
    assertEquals(11.0, r.recommended!!.khaMarginA!!, 1e-9)
    assertEquals(10.0, r.smaller!!.sectionMm2)
    assertEquals(-4.0, r.smaller!!.khaMarginA!!, 1e-9)
    assertEquals(false, r.smaller!!.khaOk)
    assertEquals(30.0, r.larger!!.khaMarginA!!, 1e-9)

    // Faktor koreksi: 61*0.87*0.8 = 42.456 A < 50 -> 16 mm2 gagal, 25 mm2 = 80*0.696 = 55.68 A lolos
    val derated = (CableCalculator.calculate(
      CableCircuit.SINGLE_PHASE, lengthM = 5.0, voltageV = 220.0, loadValue = 50.0, cosPhi = 0.9, maxDropLimitPercent = 4.0,
      khaTable = table, tempCorrectionFactor = 0.87, groupCorrectionFactor = 0.8,
    ) as CableOutcome.Success).result
    assertEquals(25.0, derated.recommended!!.sectionMm2)
    assertEquals(55.68, derated.recommended!!.deratedKhaA!!, 1e-9)

    // Ukuran > 25 tidak ada di tabel -> 200 A tidak punya rekomendasi
    val none = (CableCalculator.calculate(
      CableCircuit.SINGLE_PHASE, lengthM = 5.0, voltageV = 220.0, loadValue = 200.0, cosPhi = 0.9, maxDropLimitPercent = 4.0, khaTable = table,
    ) as CableOutcome.Success).result
    assertNull(none.recommended)

    // Simpan/baca tabel
    val decoded = KhaTableCodec.decode(KhaTableCodec.encode(listOf(table, KhaTable("C|tray", mapOf(2.5 to 27.0)))))
    assertEquals(listOf(table, KhaTable("C tray", mapOf(2.5 to 27.0))), decoded)
    assertEquals(emptyList(), KhaTableCodec.decode(null))
    assertEquals(CableOutcome.Failure(CableError.INVALID_CORRECTION_FACTOR), CableCalculator.calculate(
      CableCircuit.SINGLE_PHASE, lengthM = 5.0, voltageV = 220.0, loadValue = 50.0, cosPhi = 0.9, maxDropLimitPercent = 4.0, khaTable = table, tempCorrectionFactor = 0.0,
    ))
  }

  // 4. Kapasitor Bank: P = 100 kW, cos φ awal = 0,70 ke target = 0,95
  @Test
  fun capacitorBank100kWFrom0_70To0_95() {
    // Hitungan tangan: P=100 kW, V=400 V, tanPhi1=1.02020406, tanPhi2=0.32868411 -> Qc = 100*(1.02020406-0.32868411) = 69.151996 kVAR
    // I1 = 206.196521 A, I2 = 151.934279 A -> dI = 54.262242 A (26.315789 %); 6 step -> std 12.5 kVAR (total 75 kVAR).
    val res = (CapacitorBankCalculator.calculate(100.0, 400.0, 0.70, 0.95) as CapacitorBankOutcome.Success).result
    assertEquals(1.020204, res.tanPhi1, 1e-5)
    assertEquals(0.328684, res.tanPhi2, 1e-5)
    assertEquals(69.151996, res.requiredKvar, 1e-5)
    assertEquals(206.196521, res.initialLineCurrentA, 1e-5)
    assertEquals(151.934279, res.targetLineCurrentA, 1e-5)
    assertEquals(54.262242, res.currentReductionA, 1e-5)
    assertEquals(26.315789, res.currentReductionPercent, 1e-5)
    val step6 = res.stepSuggestions.first { it.stepCount == 6 }
    assertEquals(12.5, step6.standardStepKvar, 1e-6)
    assertEquals(75.0, step6.totalInstalledKvar, 1e-6)
  }

  // 5. Ketidakseimbangan Tegangan 3 Fasa (NEMA MG 1): 398 / 400 / 410 V
  @Test
  fun phaseUnbalance398_400_410VMatchesNemaMg1() {
    // Hitungan tangan: V_avg = (398+400+410)/3 = 402.666667 V; dev_maks = 7.333333 V -> %Unb = 1.821192 % (>1% WARNING); dT = 2*(1.821192)^2 = 6.633481 %.
    val res = (PhaseUnbalanceCalculator.calculate(UnbalanceQuantity.VOLTAGE, 398.0, 400.0, 410.0) as PhaseUnbalanceOutcome.Success).result
    assertEquals(402.666667, res.average, 1e-5)
    assertEquals(7.333333, res.maxDeviation, 1e-5)
    assertEquals(1.821192, res.unbalancePercent, 1e-5)
    assertEquals(UnbalanceStatus.WARNING_EXCEEDS_1_PERCENT, res.status)
    assertEquals(6.633481, res.extraTempRisePercent!!, 1e-5)
  }

  // 6. Trafo 1000 kVA, 20.000 V / 400 V, Uk = 6 %
  @Test
  fun transformer1000kVA400VUk6Percent() {
    // Hitungan tangan: In1 = 1000000/(sqrt(3)*20000) = 28.867513 A; In2 = 1000000/(sqrt(3)*400) = 1443.375673 A; Isc = In2/0.06 = 24056.261216 A (24.056261 kA).
    val res = (TransformerCalculator.calculate(PhaseSystem.THREE_PHASE, TransformerInputMode.FROM_KVA, 1000.0, 20000.0, 400.0, 6.0) as TransformerOutcome.Success).result
    assertEquals(28.867513, res.primaryNominalCurrentA, 1e-5)
    assertEquals(1443.375673, res.secondaryNominalCurrentA, 1e-5)
    assertEquals(24056.261216, res.secondaryShortCircuitCurrentA, 1e-4)
    assertEquals(24.056261, res.secondaryShortCircuitCurrentKa, 1e-5)
  }

  // 7. Hukum Ohm & Daya AC 3 Fasa
  @Test
  fun ohmLawAndAcPowerCalculations() {
    // Hitungan tangan: V=230 V, R=46 Ohm -> I=5 A, P=1150 W. AC 3P: 15 kW, 400 V, cosPhi=0.85 -> S=17.647059 kVA, I=25.471335 A, Q=9.296165 kVAR.
    val ohm = (OhmCalculator.solve(v = 230.0, r = 46.0) as OhmOutcome.Success).result
    assertEquals(5.0, ohm.currentA, 1e-9)
    assertEquals(1150.0, ohm.powerW, 1e-9)
    val ac3 = (AcPowerCalculator.calculate(PhaseSystem.THREE_PHASE, 400.0, AcPowerInputMode.FROM_ACTIVE_POWER_KW, 15.0, 0.85) as AcPowerOutcome.Success).result
    assertEquals(17.647059, ac3.apparentPowerKva, 1e-5)
    assertEquals(25.471335, ac3.currentA, 1e-5)
    assertEquals(9.296165, ac3.reactivePowerKvar, 1e-5)
  }

  // 8. Kasus tepi input tidak valid (nol, negatif, kosong, cos φ / efisiensi di luar 0..1)
  @Test
  fun invalidInputsReturnExplicitErrorsWithoutNaNOrCrash() {
    assertEquals(OhmOutcome.Failure(OhmError.NEED_EXACTLY_TWO), OhmCalculator.solve(v = 230.0))
    assertEquals(OhmOutcome.Failure(OhmError.NEED_EXACTLY_TWO), OhmCalculator.solve(v = 230.0, i = 10.0, r = 23.0))
    assertEquals(OhmOutcome.Failure(OhmError.NON_POSITIVE_VALUE), OhmCalculator.solve(v = 0.0, i = 10.0))
    assertEquals(OhmOutcome.Failure(OhmError.NON_POSITIVE_VALUE), OhmCalculator.solve(v = -12.0, r = 4.0))
    assertEquals(AcPowerOutcome.Failure(AcPowerError.INVALID_COS_PHI), AcPowerCalculator.calculate(PhaseSystem.THREE_PHASE, 400.0, AcPowerInputMode.FROM_CURRENT, 10.0, 0.0))
    assertEquals(AcPowerOutcome.Failure(AcPowerError.INVALID_COS_PHI), AcPowerCalculator.calculate(PhaseSystem.THREE_PHASE, 400.0, AcPowerInputMode.FROM_CURRENT, 10.0, 1.05))
    assertEquals(AcPowerOutcome.Failure(AcPowerError.INVALID_VOLTAGE), AcPowerCalculator.calculate(PhaseSystem.THREE_PHASE, -400.0, AcPowerInputMode.FROM_CURRENT, 10.0, 0.85))
    assertEquals(MotorOutcome.Failure(MotorError.INVALID_POWER), MotorCalculator.calculate(0.0, MotorPowerUnit.KW, 400.0, 0.85, 0.90))
    assertEquals(MotorOutcome.Failure(MotorError.INVALID_COS_PHI), MotorCalculator.calculate(7.5, MotorPowerUnit.KW, 400.0, 1.2, 0.90))
    assertEquals(MotorOutcome.Failure(MotorError.INVALID_COS_PHI), MotorCalculator.calculate(7.5, MotorPowerUnit.KW, 400.0, -0.5, 0.90))
    assertEquals(MotorOutcome.Failure(MotorError.INVALID_EFFICIENCY), MotorCalculator.calculate(7.5, MotorPowerUnit.KW, 400.0, 0.85, 0.0))
    assertEquals(MotorOutcome.Failure(MotorError.INVALID_EFFICIENCY), MotorCalculator.calculate(7.5, MotorPowerUnit.KW, 400.0, 0.85, 1.05))
    assertEquals(CableOutcome.Failure(CableError.INVALID_LENGTH), CableCalculator.calculate(CableCircuit.THREE_PHASE, lengthM = 0.0, voltageV = 400.0, loadValue = 50.0, cosPhi = 0.85, maxDropLimitPercent = 5.0))
    assertEquals(CapacitorBankOutcome.Failure(CapacitorBankError.TARGET_NOT_GREATER_THAN_INITIAL), CapacitorBankCalculator.calculate(100.0, 400.0, 0.90, 0.85))
    assertEquals(CapacitorBankOutcome.Failure(CapacitorBankError.INVALID_INITIAL_COS_PHI), CapacitorBankCalculator.calculate(100.0, 400.0, 1.0, 0.95))
    assertEquals(PhaseUnbalanceOutcome.Failure(PhaseUnbalanceError.INVALID_OR_NON_POSITIVE_VALUES), PhaseUnbalanceCalculator.calculate(UnbalanceQuantity.VOLTAGE, 400.0, 0.0, 400.0))
    assertEquals(PhaseUnbalanceOutcome.Failure(PhaseUnbalanceError.INVALID_OR_NON_POSITIVE_VALUES), PhaseUnbalanceCalculator.calculate(UnbalanceQuantity.VOLTAGE, 400.0, null, 400.0))
    assertEquals(TransformerOutcome.Failure(TransformerError.INVALID_UK_PERCENT), TransformerCalculator.calculate(inputValue = 1000.0, primaryVoltageV = 20000.0, secondaryVoltageV = 400.0, ukPercent = 0.0))
  }

  // 9. ProEntitlement: Trial 3 hari & Rewarded 24 jam
  @Test
  fun proEntitlementTrialAndRewardedBoundaries() {
    val start = 1_700_000_000_000L
    val end = start + ProEntitlement.TRIAL_DURATION_MS
    assertFalse(ProEntitlement.isTrialActive(0L, start))
    assertTrue(ProEntitlement.isTrialActive(start, start))
    assertEquals(3, ProEntitlement.trialDaysLeft(ProEntitlement.trialRemainingMillis(start, start)))
    assertFalse(ProEntitlement.isTrialActive(start, end))
    assertTrue(ProEntitlement.isTrialExpired(start, end))
    assertEquals(start + ProEntitlement.REWARD_DURATION_MS, ProEntitlement.clampRewardedUntil(start + 7 * ProEntitlement.DAY_MS, start))
  }
}
