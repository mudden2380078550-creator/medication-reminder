# 安心服药 Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Build a GPL-3.0, offline-first Android medication reminder that reliably records confirmed doses, forecasts stock, shows senior-friendly alarm-style reminders, and produces an installable APK.

**Architecture:** A single Android application module uses Compose for UI, Room for durable records, DataStore for simple preferences, pure Kotlin rule engines for schedules and stock, and a PendingIntent-based AlarmManager adapter for system reminders. Manual dependency injection keeps platform code behind interfaces so critical domain and state-transition behavior is deterministic and testable.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 9.2.0, Gradle 9.4.1, JDK 17, compileSdk/targetSdk 37, minSdk 26, Compose BOM 2026.06.00, Material 3 1.4.0, Activity Compose 1.13.0, Lifecycle 2.11.0, Navigation Compose 2.9.8, Room 2.8.4 with KSP 2.3.10, DataStore 1.2.1, kotlinx.serialization JSON 1.11.0, kotlinx.coroutines 1.11.0, JUnit 4.13.2, AndroidX Test Runner 1.7.0, Espresso 3.7.0.

## Global Constraints

- Display name: 安心服药. Application ID and namespace: org.openmeds.reminder.
- Android 8.0/API 26 is the minimum; compileSdk and targetSdk are 37.
- GPL-3.0 license; no proprietary runtime dependency.
- Do not declare android.permission.INTERNET.
- No account, cloud sync, analytics, ads, crash upload, drug recommendation, diagnosis, or interaction checking.
- Supported schedules: daily fixed times, selected weekdays, every N days, and optional course end date.
- Deferred schedules: every N hours and automated as-needed dosing.
- Quantities are stored as Long milli-units; binary floating-point is forbidden for stock and doses.
- Only TAKEN consumes stock. SKIPPED, SNOOZED, and UNCONFIRMED do not.
- Initial dose alarm plus retries at +10, +20, and +30 minutes; finalize UNCONFIRMED at +40 minutes.
- Low-stock threshold is fixed at seven days; notification time defaults to 09:00 and is user-configurable.
- Main body text is at least 16sp, medicine names 20sp, primary time 30sp, and primary touch targets 56dp.
- Full-screen reminder is best effort and must visibly degrade when exact-alarm, notification, or full-screen access is absent.
- Each task ends with focused tests and a commit. Do not combine commits across tasks.

---

## Planned File Structure

- settings.gradle.kts — repository and module configuration.
- build.gradle.kts — root plugin declarations.
- gradle/libs.versions.toml — pinned plugin and library versions.
- gradle/wrapper/*, gradlew, gradlew.bat — reproducible Gradle 9.4.1 wrapper.
- scripts/bootstrap-android.ps1 — portable JDK 17 and Android SDK bootstrap under work/toolchain.
- scripts/build-release.ps1 — one-command clean tests, lint, and release APK build.
- app/build.gradle.kts — Android application configuration and dependencies.
- app/src/main/AndroidManifest.xml — permissions, activities, and receivers; deliberately no INTERNET.
- app/src/main/java/org/openmeds/reminder/domain/model/* — immutable domain types.
- app/src/main/java/org/openmeds/reminder/domain/schedule/* — recurrence calculation.
- app/src/main/java/org/openmeds/reminder/domain/inventory/* — depletion forecast and low-stock rules.
- app/src/main/java/org/openmeds/reminder/data/db/* — Room entities, DAOs, database, and mappers.
- app/src/main/java/org/openmeds/reminder/data/repository/* — transactional repository implementation.
- app/src/main/java/org/openmeds/reminder/reminder/* — event planning, AlarmManager adapter, receivers, notification actions, capability checks.
- app/src/main/java/org/openmeds/reminder/ui/* — theme, navigation, screens, ViewModels, and full-screen reminder activity.
- app/src/main/java/org/openmeds/reminder/backup/* — validated versioned JSON import/export.
- app/src/test/* — pure JVM schedule, inventory, orchestration, and ViewModel tests.
- app/src/androidTest/* — Room, Compose accessibility, manifest, and notification integration tests.
- README.md, CONTRIBUTING.md, LICENSE — public project documentation.

---

### Task 1: Reproducible Toolchain and Buildable App Shell

**Files:**
- Create: scripts/bootstrap-android.ps1
- Create: scripts/build-release.ps1
- Modify: .gitignore
- Create: settings.gradle.kts
- Create: build.gradle.kts
- Create: gradle.properties
- Create: gradle/libs.versions.toml
- Create: gradle/wrapper/gradle-wrapper.properties
- Create: gradle/wrapper/gradle-wrapper.jar
- Create: gradlew
- Create: gradlew.bat
- Create: app/build.gradle.kts
- Create: app/proguard-rules.pro
- Create: app/src/main/AndroidManifest.xml
- Create: app/src/main/res/values/strings.xml
- Create: app/src/main/res/values/themes.xml
- Create: app/src/main/java/org/openmeds/reminder/MedicationApplication.kt
- Create: app/src/main/java/org/openmeds/reminder/MainActivity.kt
- Test: app/src/test/java/org/openmeds/reminder/ProjectSmokeTest.kt

**Interfaces:**
- Produces: a Gradle wrapper and .\gradlew.bat testDebugUnitTest, lintDebug, and assembleDebug commands.
- Produces: MedicationApplication and MainActivity entry points used by all later tasks.

- [ ] **Step 1: Add the failing smoke test and minimal manifest contract**

~~~kotlin
package org.openmeds.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test fun packageNameIsStable() {
        assertEquals("org.openmeds.reminder", MedicationApplication.PACKAGE_ID)
    }
}
~~~

The manifest must declare the application and MainActivity but must not declare INTERNET.

- [ ] **Step 2: Bootstrap the portable build tools**

scripts/bootstrap-android.ps1 downloads into work/toolchain only:

~~~powershell
$jdkUri = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk'
$gradleUri = 'https://services.gradle.org/distributions/gradle-9.4.1-bin.zip'
$gradleSha256 = '2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb'
$sdkUri = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
$sdkSha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
~~~

The script verifies both fixed checksums, installs platform-tools, platforms;android-37.0, build-tools;36.0.0, emulator, and system-images;android-37.0;google_apis;x86_64, accepts the Android SDK license only after the user authorizes the download, creates the AnxinApi37 AVD, and writes local.properties with sdk.dir pointing at work/toolchain/android-sdk. The `.0` suffix is the Android SDK repository package identifier for API 37; the app configuration remains compileSdk/targetSdk 37. Add work/ to .gitignore.

Run: powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-android.ps1
Expected: Java 17, sdkmanager, adb, and Gradle 9.4.1 version lines with no checksum error.

- [ ] **Step 3: Create the pinned Gradle project**

Use AGP 9.2.0, built-in Kotlin upgraded to 2.3.21, Compose compiler and serialization plugins 2.3.21, KSP 2.3.10, Compose BOM 2026.06.00, kotlinx.serialization JSON 1.11.0, kotlinx.coroutines 1.11.0, and Java 17. Set minSdk 26, compileSdk 37, targetSdk 37, versionCode 1, versionName 0.1.0.

~~~kotlin
android {
    namespace = "org.openmeds.reminder"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.openmeds.reminder"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
~~~

- [ ] **Step 4: Run the test and build**

Run: .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --stacktrace
Expected: ProjectSmokeTest PASS, lint succeeds, and app/build/outputs/apk/debug/app-debug.apk exists.

- [ ] **Step 5: Commit**

~~~powershell
git add scripts gradle gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties app
git commit -m "build: bootstrap Android application"
~~~

---

### Task 2: Domain Types and Recurrence Engine

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/domain/model/MilliUnits.kt
- Create: app/src/main/java/org/openmeds/reminder/domain/model/Medication.kt
- Create: app/src/main/java/org/openmeds/reminder/domain/model/MedicationSchedule.kt
- Create: app/src/main/java/org/openmeds/reminder/domain/model/DoseEvent.kt
- Create: app/src/main/java/org/openmeds/reminder/domain/model/MedicationPlanInput.kt
- Create: app/src/main/java/org/openmeds/reminder/domain/schedule/ScheduleEngine.kt
- Test: app/src/test/java/org/openmeds/reminder/domain/schedule/ScheduleEngineTest.kt

**Interfaces:**
- Produces: MilliUnits(Long), SignedMilliUnits(Long), Medication, MedicationPlanInput, MedicationSchedule, ScheduleRule, DoseEvent, DoseState.
- Produces: ScheduleEngine.nextOccurrence(schedule, afterExclusive, zoneId): Instant?
- Produces: ScheduleEngine.occurrencesBetween(schedule, fromInclusive, toExclusive, zoneId): List<Instant>

- [ ] **Step 1: Write failing recurrence and fixed-point tests**

~~~kotlin
@Test fun weeklyScheduleSkipsUnselectedDays() {
    val schedule = fixtureWeekly(days = setOf(DayOfWeek.MONDAY), time = LocalTime.of(9, 0))
    val next = engine.nextOccurrence(
        schedule,
        Instant.parse("2026-08-17T01:01:00Z"),
        ZoneId.of("Asia/Shanghai")
    )
    assertEquals(Instant.parse("2026-08-24T01:00:00Z"), next)
}

@Test fun everyThreeDaysIsAnchoredToStartDate() {
    val schedule = fixtureEveryNDays(3, LocalDate.parse("2026-08-01"), LocalTime.of(8, 0))
    assertEquals(
        Instant.parse("2026-08-07T00:00:00Z"),
        engine.nextOccurrence(schedule, Instant.parse("2026-08-05T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
    )
}

@Test fun halfTabletUsesExactMilliUnits() {
    assertEquals(1_500L, MilliUnits.fromDecimal("1.5").value)
}
~~~

- [ ] **Step 2: Run tests to verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*ScheduleEngineTest"
Expected: FAIL because domain types and ScheduleEngine do not exist.

- [ ] **Step 3: Implement immutable domain types and recurrence**

~~~kotlin
@JvmInline
value class MilliUnits(val value: Long) {
    init { require(value >= 0) }
    operator fun minus(other: MilliUnits) = SignedMilliUnits(value - other.value)
    companion object { fun fromDecimal(text: String): MilliUnits }
}

@JvmInline
value class SignedMilliUnits(val value: Long) {
    operator fun minus(other: MilliUnits) = SignedMilliUnits(value - other.value)
    companion object { fun fromDecimal(text: String): SignedMilliUnits }
}

sealed interface ScheduleRule {
    data class Daily(val times: List<LocalTime>) : ScheduleRule
    data class Weekly(val days: Set<DayOfWeek>, val times: List<LocalTime>) : ScheduleRule
    data class EveryNDays(val intervalDays: Int, val anchorDate: LocalDate, val times: List<LocalTime>) : ScheduleRule
}

data class MedicationSchedule(
    val id: Long,
    val medicationId: Long,
    val dose: MilliUnits,
    val rule: ScheduleRule,
    val startDate: LocalDate,
    val endDate: LocalDate?
)
~~~

Medication.stock uses SignedMilliUnits because an honestly recorded dose may take stock below zero. MedicationSchedule.dose uses non-negative MilliUnits. Sort and deduplicate times. Resolve LocalDateTime with atZone so DST gaps move to the first valid instant and overlaps select the earlier offset. Enforce intervalDays >= 1, non-empty times, and endDate >= startDate.

- [ ] **Step 4: Add boundary tests and run the suite**

Add tests for multiple times per day, course end exclusion, leap day, DST gap, DST overlap only once, and afterExclusive equality.

Run: .\gradlew.bat testDebugUnitTest --tests "*ScheduleEngineTest"
Expected: PASS.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder/domain app/src/test/java/org/openmeds/reminder/domain
git commit -m "feat: add medication schedule engine"
~~~

---

### Task 3: Inventory Forecasting and Seven-Day Rule

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/domain/inventory/InventoryForecast.kt
- Create: app/src/main/java/org/openmeds/reminder/domain/inventory/InventoryForecaster.kt
- Test: app/src/test/java/org/openmeds/reminder/domain/inventory/InventoryForecasterTest.kt

**Interfaces:**
- Consumes: Medication, MedicationSchedule, MilliUnits, ScheduleEngine.
- Produces: InventoryForecast(depletesAt: Instant?, remainingAtCourseEnd: SignedMilliUnits?, nextDoseShortfall: Boolean, daysRemaining: Long?).
- Produces: InventoryForecaster.forecast(medication, schedules, now, zoneId): InventoryForecast.
- Produces: InventoryForecaster.needsDailyLowStockReminder(forecast): Boolean.

- [ ] **Step 1: Write failing forecast tests**

~~~kotlin
@Test fun sixDailyTabletsTriggersSevenDayReminder() {
    val result = forecaster.forecast(
        medication = fixtureMedication(stock = "6"),
        schedules = listOf(fixtureDaily(dose = "1", time = "09:00")),
        now = Instant.parse("2026-08-14T00:00:00Z"),
        zoneId = ZoneId.of("Asia/Shanghai")
    )
    assertEquals(6L, result.daysRemaining)
    assertTrue(forecaster.needsDailyLowStockReminder(result))
}

@Test fun courseEndingBeforeDepletionDoesNotNotify() {
    val result = forecaster.forecast(fixtureMedication("30"), listOf(fixtureThreeDayCourse()), NOW, ZONE)
    assertNull(result.depletesAt)
    assertFalse(forecaster.needsDailyLowStockReminder(result))
}
~~~

- [ ] **Step 2: Verify the tests fail**

Run: .\gradlew.bat testDebugUnitTest --tests "*InventoryForecasterTest"
Expected: FAIL because InventoryForecaster is missing.

- [ ] **Step 3: Implement bounded forward simulation**

~~~kotlin
class InventoryForecaster(private val scheduleEngine: ScheduleEngine) {
    fun forecast(
        medication: Medication,
        schedules: List<MedicationSchedule>,
        now: Instant,
        zoneId: ZoneId
    ): InventoryForecast

    fun needsDailyLowStockReminder(value: InventoryForecast): Boolean =
        value.daysRemaining != null && value.daysRemaining <= 7
}
~~~

Merge all active occurrences in chronological order, subtract signed milli-units, stop at first shortfall, and stop when all finite courses end. For open-ended schedules, search 366 days; if no depletion appears, report daysRemaining greater than 365 rather than inventing a date.

- [ ] **Step 4: Run exact-quantity and multi-plan tests**

Add tests for half tablets, milliliters, two schedules consuming one stock, zero stock, negative correction state, no schedule, exactly seven days, eight days, and a finite course.

Run: .\gradlew.bat testDebugUnitTest --tests "*InventoryForecasterTest"
Expected: PASS.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder/domain/inventory app/src/test/java/org/openmeds/reminder/domain/inventory
git commit -m "feat: forecast medication inventory"
~~~

---

### Task 4: Room Schema and Atomic Repository

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/data/db/Entities.kt
- Create: app/src/main/java/org/openmeds/reminder/data/db/MedicationDao.kt
- Create: app/src/main/java/org/openmeds/reminder/data/db/ScheduleDao.kt
- Create: app/src/main/java/org/openmeds/reminder/data/db/DoseEventDao.kt
- Create: app/src/main/java/org/openmeds/reminder/data/db/StockTransactionDao.kt
- Create: app/src/main/java/org/openmeds/reminder/data/db/AppDatabase.kt
- Create: app/src/main/java/org/openmeds/reminder/data/db/DbMappers.kt
- Create: app/src/main/java/org/openmeds/reminder/data/repository/MedicationRepository.kt
- Create: app/src/main/java/org/openmeds/reminder/data/repository/RoomMedicationRepository.kt
- Create: app/src/main/java/org/openmeds/reminder/AppContainer.kt
- Modify: app/src/main/java/org/openmeds/reminder/MedicationApplication.kt
- Test: app/src/androidTest/java/org/openmeds/reminder/data/RoomMedicationRepositoryTest.kt

**Interfaces:**
- Produces: HomeData(medications: List<Medication>, schedules: List<MedicationSchedule>, nextDose: DoseEvent?).
- Produces: MedicationRepository.observeHome(): Flow<HomeData>.
- Produces: createMedication(input: MedicationPlanInput): Long; updateMedication(id, input); addStock(id, amount, note); correctStock(id, amount, note).
- Produces: recordDoseAction(eventId, DoseAction, actedAt): DoseActionResult.
- Produces: actionableEventsForMinute(epochMinute): List<DoseEvent>.
- Produces: schedule(id), event(id), pendingEvents(), insertDoseEventIfAbsent(scheduleId, scheduledAt), and markUnconfirmedIfActionable(eventId, at) for reminder orchestration.

- [ ] **Step 1: Write failing in-memory Room tests**

~~~kotlin
@Test fun repeatedTakenActionConsumesStockOnce() = runTest {
    val eventId = repository.insertFixtureEvent(stock = MilliUnits(10_000), dose = MilliUnits(1_000))
    assertEquals(DoseActionResult.Applied, repository.recordDoseAction(eventId, DoseAction.TAKEN, NOW))
    assertEquals(DoseActionResult.AlreadyHandled, repository.recordDoseAction(eventId, DoseAction.TAKEN, NOW))
    assertEquals(9_000L, repository.medication(eventId).stock.value)
    assertEquals(1, repository.stockTransactions(eventId).size)
}
~~~

- [ ] **Step 2: Verify the instrumentation test fails**

Run: .\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.openmeds.reminder.data.RoomMedicationRepositoryTest
Expected: FAIL because schema and repository are missing.

- [ ] **Step 3: Implement normalized schema version 1**

Use medication, schedule, schedule_time, dose_event, and stock_transaction tables. Store dates as epochDay, times as minuteOfDay, timestamps as epoch milliseconds, weekday selection as a seven-bit mask, and quantities as Long milli-units. Add foreign keys and indices on medication_id, schedule_id, state, and scheduled_at.

~~~kotlin
@Transaction
override suspend fun recordDoseAction(
    eventId: Long,
    action: DoseAction,
    actedAt: Instant
): DoseActionResult {
    val event = eventDao.byId(eventId) ?: return DoseActionResult.NotFound
    if (event.state in setOf(DoseState.TAKEN, DoseState.SKIPPED)) {
        return DoseActionResult.AlreadyHandled
    }
    if (action == DoseAction.SNOOZE) {
        eventDao.setSnoozed(eventId, actedAt.toEpochMilli())
        return DoseActionResult.Applied
    }
    if (action == DoseAction.TAKEN) {
        medicationDao.adjustStock(event.medicationId, -event.doseMilliUnits)
        stockDao.insert(StockTransactionEntity.consume(event, actedAt))
    }
    eventDao.setTerminalState(eventId, action.toState(), actedAt.toEpochMilli())
    return DoseActionResult.Applied
}
~~~

- [ ] **Step 4: Run repository tests**

Add tests for skipped/no deduction, unconfirmed/no deduction, snooze retry update, negative stock preservation, schedule replacement in one transaction, soft-delete preserving history, and failed import rollback.

Run: .\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.openmeds.reminder.data.RoomMedicationRepositoryTest
Expected: PASS.

- [ ] **Step 5: Export Room schema and commit**

Run: .\gradlew.bat kspDebugKotlin
Expected: app/schemas/org.openmeds.reminder.data.db.AppDatabase/1.json exists.

~~~powershell
git add app/src/main/java/org/openmeds/reminder/data app/src/main/java/org/openmeds/reminder/AppContainer.kt app/src/main/java/org/openmeds/reminder/MedicationApplication.kt app/src/androidTest/java/org/openmeds/reminder/data app/schemas
git commit -m "feat: persist medications and dose history"
~~~

---

### Task 5: Dose Event Planning and Reminder Orchestration

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/reminder/ReminderScheduler.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/ReminderNotifier.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/ZoneProvider.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/EventPlanner.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/ReminderOrchestrator.kt
- Modify: app/src/main/java/org/openmeds/reminder/AppContainer.kt
- Test: app/src/test/java/org/openmeds/reminder/reminder/ReminderOrchestratorTest.kt

**Interfaces:**
- Produces: ReminderScheduler.scheduleDose(event), scheduleRetry(eventId, attempt, at), scheduleFinalize(eventId, at), cancelEvent(eventId), scheduleLowStock(medicationId, at).
- Produces: ReminderNotifier.showDoseBatch(epochMinute) and dismissDoseBatch(epochMinute).
- Produces: ZoneProvider.current(): ZoneId.
- Produces: EventPlanner.ensureNextEvent(scheduleId, afterExclusive, zoneId): DoseEvent?.
- Produces: ReminderOrchestrator.onInitialAlarm(eventId, firedAt), onRetry(eventId, attempt, firedAt), onSnoozed(eventId, actedAt), onFinalize(eventId, firedAt), rescheduleAll(reason).

- [ ] **Step 1: Write failing orchestration tests with fakes**

~~~kotlin
@Test fun initialAlarmSchedulesOnlyNextRetryAndNextDose() = runTest {
    orchestrator.onInitialAlarm(EVENT_ID, NOW)
    assertEquals(listOf(10L), fakeScheduler.retryMinutesAfterNow)
    assertTrue(fakeScheduler.finalizeMinutesAfterNow.isEmpty())
    assertEquals(1, fakePlanner.ensureNextCalls)
}
~~~

- [ ] **Step 2: Verify the tests fail**

Run: .\gradlew.bat testDebugUnitTest --tests "*ReminderOrchestratorTest"
Expected: FAIL because orchestration classes are missing.

- [ ] **Step 3: Implement event planning and state guards**

~~~kotlin
suspend fun onInitialAlarm(eventId: Long, firedAt: Instant) {
    val event = repository.event(eventId) ?: return
    if (event.state != DoseState.PENDING) return
    notifier.showDoseReminder(event.scheduledAt)
    scheduler.scheduleRetry(eventId, 1, firedAt.plus(10, ChronoUnit.MINUTES))
    planner.ensureNextEvent(event.scheduleId, event.scheduledAt, zoneProvider.current())
}
~~~

Each retry re-checks state before alerting and schedules only the next link: attempt 1 schedules attempt 2 at +10 minutes, attempt 2 schedules attempt 3, and attempt 3 schedules finalization. With no interaction this yields alerts at +10, +20, and +30 and UNCONFIRMED at +40. SNOOZE cancels the currently scheduled next link and schedules that same next attempt exactly ten minutes from the user action, so duplicate alarms cannot accumulate. Finalize changes only PENDING or SNOOZED to UNCONFIRMED. A TAKEN or SKIPPED action cancels the remaining retry or finalize alarm.

- [ ] **Step 4: Run state-race tests**

Add tests for action before retry, duplicate broadcast, finalization after TAKEN, repeated snooze without duplicate alarms, rescheduling past occurrences as one UNCONFIRMED event instead of multiple popups, schedule edit cancelation, and two medicines in the same epoch minute sharing one notification batch.

Run: .\gradlew.bat testDebugUnitTest --tests "*ReminderOrchestratorTest"
Expected: PASS.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder/reminder app/src/main/java/org/openmeds/reminder/AppContainer.kt app/src/test/java/org/openmeds/reminder/reminder
git commit -m "feat: orchestrate dose reminder events"
~~~

---

### Task 6: Android Alarm Adapter, Permissions, and System Receivers

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/reminder/AndroidReminderScheduler.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/AlarmReceiver.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/SystemRestoreReceiver.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/ReminderCapabilityChecker.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/PendingIntentFactory.kt
- Modify: app/src/main/java/org/openmeds/reminder/AppContainer.kt
- Modify: app/src/main/AndroidManifest.xml
- Test: app/src/test/java/org/openmeds/reminder/reminder/PendingIntentFactoryTest.kt
- Test: app/src/androidTest/java/org/openmeds/reminder/reminder/ReminderCapabilityCheckerTest.kt

**Interfaces:**
- Consumes: ReminderScheduler and ReminderOrchestrator from Task 5.
- Produces: ReminderCapabilitySnapshot(notifications, exactAlarms, fullScreen, batteryRestricted).
- Produces stable request codes derived from eventId and alarm kind without collisions inside one event.

- [ ] **Step 1: Write failing request-code and capability tests**

~~~kotlin
@Test fun retryKindsHaveDistinctStableRequestCodes() {
    val values = (0..4).map { factory.requestCode(42L, AlarmKind.retryOrFinalize(it)) }
    assertEquals(values.size, values.toSet().size)
    assertEquals(values, (0..4).map { factory.requestCode(42L, AlarmKind.retryOrFinalize(it)) })
}
~~~

- [ ] **Step 2: Verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*PendingIntentFactoryTest"
Expected: FAIL because factory is missing.

- [ ] **Step 3: Implement PendingIntent-based alarms**

Use RTC_WAKEUP and setExactAndAllowWhileIdle when canScheduleExactAlarms is true. Otherwise use setAndAllowWhileIdle and expose DegradedExactAlarm in capability state. Never use OnAlarmListener because it is process-bound.

Manifest permissions and receivers:

~~~xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
~~~

SystemRestoreReceiver handles BOOT_COMPLETED, TIMEZONE_CHANGED, TIME_SET, MY_PACKAGE_REPLACED, and exact-alarm permission grant. It delegates to rescheduleAll and performs no database work on the main thread.

- [ ] **Step 4: Run unit, manifest, and capability tests**

Run: .\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
Expected: request-code tests pass; the merged manifest contains the four intended permissions and no INTERNET permission.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/org/openmeds/reminder/reminder app/src/main/java/org/openmeds/reminder/AppContainer.kt app/src/test/java/org/openmeds/reminder/reminder app/src/androidTest/java/org/openmeds/reminder/reminder
git commit -m "feat: schedule resilient Android alarms"
~~~

---

### Task 7: Full-Screen Dose Reminder and Notification Actions

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/reminder/DoseNotificationController.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/DoseActionReceiver.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/reminder/ReminderActivity.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/reminder/ReminderScreen.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/reminder/ReminderViewModel.kt
- Create: app/src/main/java/org/openmeds/reminder/settings/ReminderPreferences.kt
- Modify: app/src/main/java/org/openmeds/reminder/AppContainer.kt
- Modify: app/src/main/AndroidManifest.xml
- Test: app/src/test/java/org/openmeds/reminder/ui/reminder/ReminderViewModelTest.kt
- Test: app/src/androidTest/java/org/openmeds/reminder/ui/reminder/ReminderScreenTest.kt

**Interfaces:**
- Consumes: actionableEventsForMinute, recordDoseAction, ReminderOrchestrator.
- Produces: ReminderUiState with one row per medication and independent actions.
- Produces: DoseNotificationController.show(epochMinute) using CATEGORY_ALARM and a fullScreenIntent when permitted.
- Produces: ReminderPreferences.flow: Flow<ReminderSettings> with sound, vibration, and low-stock-time defaults.

- [ ] **Step 1: Write failing ViewModel and Compose tests**

~~~kotlin
@Test fun sameMinuteMedicinesRequireSeparateConfirmation() = runTest {
    viewModel.load(EPOCH_MINUTE)
    assertEquals(2, viewModel.state.value.items.size)
    viewModel.take(EVENT_A)
    assertEquals(DoseState.TAKEN, viewModel.state.value.item(EVENT_A).state)
    assertEquals(DoseState.PENDING, viewModel.state.value.item(EVENT_B).state)
}
~~~

Compose test assertions: medication name, dose, scheduled time, 已服用, 10 分钟后提醒, and 跳过 are visible with 200% font scale; all action nodes have minimum 56dp height.

- [ ] **Step 2: Verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*ReminderViewModelTest"
Expected: FAIL because reminder UI classes are missing.

- [ ] **Step 3: Implement the alarm-style activity**

ReminderActivity calls setShowWhenLocked(true) and setTurnScreenOn(true), uses a dedicated alarm notification channel, and does not intercept Home or system navigation. The page closes automatically only after every item is TAKEN or SKIPPED; UNCONFIRMED stays visible and actionable until the user handles it or leaves. At +40 minutes it shows 未确认 without continuing sound.

~~~kotlin
NotificationCompat.Builder(context, DOSE_CHANNEL_ID)
    .setCategory(NotificationCompat.CATEGORY_ALARM)
    .setPriority(NotificationCompat.PRIORITY_MAX)
    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    .setOngoing(true)
    .setFullScreenIntent(fullScreenPendingIntent, true)
~~~

Use system alarm audio for at most 60 seconds per alert cycle and stop it on any action or lifecycle stop. A snooze action records SNOOZED, schedules exactly one +10-minute alarm, and does not consume stock.

- [ ] **Step 4: Run UI and state tests**

Run: .\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
Expected: reminder ViewModel and Compose tests pass; TAKEN is idempotent; leaving the activity does not cancel retry alarms.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder/reminder app/src/main/java/org/openmeds/reminder/ui/reminder app/src/main/java/org/openmeds/reminder/settings app/src/main/java/org/openmeds/reminder/AppContainer.kt app/src/main/AndroidManifest.xml app/src/test app/src/androidTest
git commit -m "feat: add full-screen dose reminders"
~~~

---

### Task 8: Senior-Friendly App Shell and Home

**Files:**
- Modify: app/src/main/java/org/openmeds/reminder/AppContainer.kt
- Modify: app/src/main/java/org/openmeds/reminder/MedicationApplication.kt
- Modify: app/src/main/java/org/openmeds/reminder/MainActivity.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/theme/Color.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/theme/Type.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/theme/Theme.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/navigation/AppDestination.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/navigation/AppNavHost.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/home/HomeViewModel.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/home/HomeScreen.kt
- Test: app/src/test/java/org/openmeds/reminder/ui/home/HomeViewModelTest.kt
- Test: app/src/androidTest/java/org/openmeds/reminder/ui/home/HomeScreenTest.kt

**Interfaces:**
- Produces: AppContainer manual dependency graph.
- Produces: HomeUiState(nextDose, medicationCards, capabilityWarnings).
- Consumes: MedicationRepository, InventoryForecaster, ReminderCapabilityChecker.

- [ ] **Step 1: Write failing home-state tests**

~~~kotlin
@Test fun lowStockCardsSortBeforeHealthyStock() = runTest {
    repository.emitHome(listOf(lowStockMedication(), healthyMedication()))
    assertEquals(true, viewModel.state.value.medicationCards.first().isLowStock)
}

@Test fun deniedExactAlarmShowsPersistentWarning() = runTest {
    capabilityFake.emit(exactAlarms = false)
    assertEquals("提醒时间可能延迟", viewModel.state.value.capabilityWarnings.single().title)
}
~~~

- [ ] **Step 2: Verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*HomeViewModelTest"
Expected: FAIL because HomeViewModel is missing.

- [ ] **Step 3: Implement the approved B layout**

Use warm off-white, deep green, amber low-stock marker, no gradients, no decorative illustration, and no dynamic color that could reduce contrast. The first card shows next dose; medicine cards show quantity and estimated days. Bottom navigation is 今日, 药箱, 记录, 设置.

The early-confirm action shows both scheduled and current time. On confirmation it records TAKEN and cancels that event alarm.

- [ ] **Step 4: Run state, layout, and accessibility tests**

Run: .\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
Expected: sorting, warnings, early-confirm dialog, 200% font scale, TalkBack labels, and 56dp target tests pass.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder app/src/test/java/org/openmeds/reminder/ui app/src/androidTest/java/org/openmeds/reminder/ui
git commit -m "feat: add senior-friendly medication home"
~~~

---

### Task 9: Add and Edit Medication Wizard

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/ui/editor/MedicationDraft.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/editor/MedicationEditorViewModel.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/editor/MedicationEditorScreen.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/editor/ScheduleSummaryFormatter.kt
- Modify: app/src/main/java/org/openmeds/reminder/ui/navigation/AppNavHost.kt
- Test: app/src/test/java/org/openmeds/reminder/ui/editor/MedicationEditorViewModelTest.kt
- Test: app/src/androidTest/java/org/openmeds/reminder/ui/editor/MedicationEditorScreenTest.kt

**Interfaces:**
- Produces: MedicationDraft.validate(): Map<Field, String>.
- Produces: ScheduleSummaryFormatter.format(draft): String.
- On save, calls repository create/update and ReminderOrchestrator.rescheduleAll(SCHEDULE_CHANGED).

- [ ] **Step 1: Write failing validation and summary tests**

~~~kotlin
@Test fun everyNDaysRequiresPositiveIntervalAndAtLeastOneTime() {
    val errors = invalidEveryNDaysDraft().validate()
    assertEquals("至少为 1 天", errors[Field.INTERVAL_DAYS])
    assertEquals("至少添加一个提醒时间", errors[Field.TIMES])
}

@Test fun summaryUsesPlainChinese() {
    assertEquals("从 8 月 15 日开始，每 3 天 09:00 服用 1 片", formatter.format(validDraft()))
}
~~~

- [ ] **Step 2: Verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*MedicationEditorViewModelTest"
Expected: FAIL because editor types are missing.

- [ ] **Step 3: Implement the three-step editor**

Step 1 collects name, unit, stock, note. Step 2 collects dose, daily/weekly/every-N-days rule, times, start date, optional end date. Step 3 displays the plain-language summary and forecast. Keep advanced recurrence collapsed until selected; do not show every-N-hours or as-needed options. MedicationDraft.toPlanInput() produces the domain MedicationPlanInput consumed by the repository; UI types never enter the data layer.

Validation blocks empty names, empty/custom blank units, non-positive dose, invalid decimal precision beyond three places, empty times, invalid weekday sets, interval below one, and end before start.

- [ ] **Step 4: Run tests and save/edit integration**

Run: .\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
Expected: validation, back navigation preserving draft, save, edit, schedule replacement, and alarm refresh tests pass.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder/ui/editor app/src/main/java/org/openmeds/reminder/ui/navigation app/src/test/java/org/openmeds/reminder/ui/editor app/src/androidTest/java/org/openmeds/reminder/ui/editor
git commit -m "feat: add medication schedule editor"
~~~

---

### Task 10: Today, History, Stock Adjustment, and Settings

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/ui/today/TodayScreen.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/today/TodayViewModel.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/history/HistoryScreen.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/history/HistoryViewModel.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/medicine/MedicineDetailScreen.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/medicine/MedicineDetailViewModel.kt
- Modify: app/src/main/java/org/openmeds/reminder/settings/ReminderPreferences.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/settings/SettingsScreen.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/settings/SettingsViewModel.kt
- Modify: app/src/main/java/org/openmeds/reminder/ui/navigation/AppNavHost.kt
- Test: app/src/test/java/org/openmeds/reminder/ui/history/HistoryViewModelTest.kt
- Test: app/src/test/java/org/openmeds/reminder/ui/settings/SettingsViewModelTest.kt

**Interfaces:**
- Produces: ReminderPreferences.flow: Flow<ReminderSettings>.
- Produces: updateSound, updateVibration, updateLowStockTime.
- Produces: history correction from UNCONFIRMED to TAKEN or SKIPPED through repository.

- [ ] **Step 1: Write failing history and settings tests**

~~~kotlin
@Test fun correctingUnconfirmedToTakenConsumesExactlyOnce() = runTest {
    viewModel.correct(EVENT_ID, DoseAction.TAKEN)
    viewModel.correct(EVENT_ID, DoseAction.TAKEN)
    assertEquals(1, repository.consumeCalls)
}

@Test fun lowStockTimeDefaultsToNine() = runTest {
    assertEquals(LocalTime.of(9, 0), preferences.flow.first().lowStockTime)
}
~~~

- [ ] **Step 2: Verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*HistoryViewModelTest" --tests "*SettingsViewModelTest"
Expected: FAIL because screens and preferences are missing.

- [ ] **Step 3: Implement focused secondary screens**

Today groups events by time and shows state text. History filters by date and medication without charts. Medicine detail shows stock transactions and provides explicit Add stock and Correct stock dialogs; correction requires a short note. Settings exposes sound, vibration, low-stock time, capability status, and links into relevant system settings.

- [ ] **Step 4: Run tests**

Run: .\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
Expected: correction, stock transaction history, settings persistence, and capability navigation tests pass.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder/ui app/src/main/java/org/openmeds/reminder/settings app/src/test app/src/androidTest
git commit -m "feat: add history stock and reminder settings"
~~~

---

### Task 11: Versioned Local Backup and Restore

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/backup/BackupDocument.kt
- Create: app/src/main/java/org/openmeds/reminder/backup/BackupCodec.kt
- Create: app/src/main/java/org/openmeds/reminder/backup/BackupService.kt
- Create: app/src/main/java/org/openmeds/reminder/ui/settings/BackupUi.kt
- Test: app/src/test/java/org/openmeds/reminder/backup/BackupCodecTest.kt
- Test: app/src/androidTest/java/org/openmeds/reminder/backup/BackupServiceTest.kt

**Interfaces:**
- Produces: BackupCodec.encode(snapshot): ByteArray and decode(bytes): BackupDocument.
- Produces: BackupService.export(uri) and validateThenRestore(uri): RestoreResult.
- BackupDocument version is integer 1 and contains no device identifier.

- [ ] **Step 1: Write failing codec and rollback tests**

~~~kotlin
@Test fun versionOneRoundTripsWithoutLosingMilliUnits() {
    val bytes = codec.encode(fixtureBackup(stockMilliUnits = 1_500L))
    assertEquals(1_500L, codec.decode(bytes).medications.single().stockMilliUnits)
}

@Test fun invalidBackupLeavesExistingDatabaseUntouched() = runTest {
    val before = repository.snapshot()
    assertTrue(service.validateThenRestore(invalidUri) is RestoreResult.Invalid)
    assertEquals(before, repository.snapshot())
}
~~~

- [ ] **Step 2: Verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*BackupCodecTest"
Expected: FAIL because backup classes are missing.

- [ ] **Step 3: Implement explicit Storage Access Framework flow**

Use ACTION_CREATE_DOCUMENT and ACTION_OPEN_DOCUMENT; do not request broad storage permissions. JSON starts with schemaVersion 1. Validate required fields, IDs, schedule constraints, and transaction references before opening a Room transaction. Create a temporary rollback snapshot, replace data atomically, then reschedule alarms. Warn in Chinese that the exported file contains sensitive medication data.

- [ ] **Step 4: Run codec and restore tests**

Run: .\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
Expected: round-trip, unknown version rejection, corrupt JSON rejection, referential validation, rollback, and successful alarm rebuild tests pass.

- [ ] **Step 5: Commit**

~~~powershell
git add app/src/main/java/org/openmeds/reminder/backup app/src/main/java/org/openmeds/reminder/ui/settings app/src/test/java/org/openmeds/reminder/backup app/src/androidTest/java/org/openmeds/reminder/backup
git commit -m "feat: add local backup and restore"
~~~

---

### Task 12: Low-Stock Daily Alarm, End-to-End QA, and Open-Source Release

**Files:**
- Create: app/src/main/java/org/openmeds/reminder/reminder/LowStockPlanner.kt
- Create: app/src/main/java/org/openmeds/reminder/reminder/LowStockReceiver.kt
- Create: app/src/test/java/org/openmeds/reminder/reminder/LowStockPlannerTest.kt
- Create: app/src/androidTest/java/org/openmeds/reminder/EndToEndReminderTest.kt
- Create: app/src/main/res/drawable/ic_launcher_foreground.xml
- Create: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- Create: app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
- Create: README.md
- Create: CONTRIBUTING.md
- Create: LICENSE
- Create: docs/verification/device-matrix.md
- Create: scripts/run-device-matrix.ps1
- Modify: app/src/main/AndroidManifest.xml
- Modify: scripts/build-release.ps1

**Interfaces:**
- Consumes: InventoryForecaster, ReminderPreferences, ReminderScheduler.
- Produces: LowStockPlanner.reconcile(now, zoneId) that schedules one 09:00/configured notification per qualifying active medication and cancels stale alarms.
- Produces: app/build/outputs/apk/release/app-release-unsigned.apk.

- [ ] **Step 1: Write failing low-stock reconciliation tests**

~~~kotlin
@Test fun stockCrossingFromEightToSevenDaysSchedulesDailyAlarm() = runTest {
    repository.emitForecast(daysRemaining = 7)
    planner.reconcile(NOW, ZONE)
    assertEquals(LocalTime.of(9, 0), scheduler.lowStockAlarms.single().localTime)
}

@Test fun restockAboveSevenDaysCancelsAlarm() = runTest {
    repository.emitForecast(daysRemaining = 20)
    planner.reconcile(NOW, ZONE)
    assertEquals(listOf(MEDICATION_ID), scheduler.cancelledLowStock)
}
~~~

- [ ] **Step 2: Verify failure**

Run: .\gradlew.bat testDebugUnitTest --tests "*LowStockPlannerTest"
Expected: FAIL because LowStockPlanner is missing.

- [ ] **Step 3: Implement daily low-stock notifications**

Low-stock notifications use a normal default-importance channel, never full-screen, never wake the screen, and do not repeat audibly. Reconcile after TAKEN, stock add/correction, schedule edits, startup, restore, time changes, and backup restore.

- [ ] **Step 4: Add public documentation and license**

README must explain: supported features, Android 8+, APK installation, notification/exact-alarm/full-screen permissions, ordinary recent-task clearing versus force stop, offline/no-INTERNET privacy, backup sensitivity, non-medical disclaimer, build commands, and GPL-3.0.

Fetch the canonical GPL-3.0 text:

~~~powershell
Invoke-WebRequest -UseBasicParsing -Uri 'https://www.gnu.org/licenses/gpl-3.0.txt' -OutFile 'LICENSE'
~~~

CONTRIBUTING requires tests for schedule, inventory, or reminder changes and forbids adding network access without a new reviewed design.

- [ ] **Step 5: Run the full verification gate**

Run:

~~~powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleRelease --stacktrace
Select-String -Path '.\app\build\intermediates\merged_manifests\debug\processDebugManifest\AndroidManifest.xml' -Pattern 'android.permission.INTERNET'
~~~

Expected: all tests and lint pass; release APK exists; Select-String returns no match.

scripts/run-device-matrix.ps1 installs system-images;android-26;google_apis;x86_64, system-images;android-31;google_apis;x86_64, system-images;android-33;google_apis;x86_64, system-images;android-34;google_apis;x86_64, and system-images;android-37;google_apis;x86_64; it creates one AVD per API, runs the instrumentation suite serially, and stops each emulator before starting the next. Record results in docs/verification/device-matrix.md for: clearing recent tasks, lock-screen alarm, reboot restore, denied permission degradation, +10/+20/+30 retries, +40 unconfirmed state, 200% font, TalkBack, same-minute medicines, and force-stop recovery after reopening. A blank or unrun row fails the release gate.

- [ ] **Step 6: Copy the release APK to outputs and commit**

~~~powershell
Copy-Item -LiteralPath '.\app\build\outputs\apk\debug\app-debug.apk' -Destination '.\outputs\anxin-medication-reminder-0.1.0-debug.apk' -Force
Copy-Item -LiteralPath '.\app\build\outputs\apk\release\app-release-unsigned.apk' -Destination '.\outputs\anxin-medication-reminder-0.1.0-release-unsigned.apk' -Force
git add app README.md CONTRIBUTING.md LICENSE docs/verification scripts/build-release.ps1
git commit -m "release: prepare medication reminder 0.1.0"
~~~

Expected: outputs/anxin-medication-reminder-0.1.0-debug.apk is immediately installable for testing; the unsigned release APK is ready for the project maintainer's persistent release key; repository working tree is clean apart from ignored outputs and local toolchain files.
