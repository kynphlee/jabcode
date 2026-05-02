# Phase 6: :diagnostic-app Assembly - Final Integration

**Duration:** 1 week (5 working days)  
**Dependencies:** All 5 modules  
**Status:** ⬜ Not Started

---

## Overview

Final assembly of diagnostic application with navigation, dependency injection, and E2E testing.

**Coverage Target:** 80%+ overall project (20 E2E tests)

---

## Day 1-2: Screen Integration

**Deliverables:**
- `DashboardScreen` composable
- `ScannerScreen` composable
- `SettingsScreen` composable
- Navigation graph
- 10 E2E tests

**Key Tests:**
```kotlin
@Test
fun `navigate from dashboard to scanner`()

@Test
fun `scan flow completes successfully`()

@Test
fun `settings persist across app restart`()
```

---

## Day 3: Dependency Injection

**Deliverables:**
- Hilt modules for each layer
- ViewModel injection
- 5 DI tests

**Setup:**
```kotlin
@HiltAndroidApp
class DiagnosticApp : Application()

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideSecureStorage(context: Context): SecureStorage {
        return StorageFactory.create(context)
    }
}
```

---

## Day 4: Performance Testing

**Deliverables:**
- Macrobenchmark tests
- Startup time ≤ 2s
- Memory ≤ 50MB
- 5 performance tests

---

## Day 5: Final Validation

**Test-Coverage-Update (Full Project):**
```bash
./gradlew clean test connectedAndroidTest jacocoTestReport

# Expected Results:
# - All 201 tests pass
# - Overall coverage: 80%+
# - No critical bugs
# - Performance targets met
```

**Release Checklist:**
- [ ] All 201 tests pass
- [ ] 80%+ coverage
- [ ] UI matches prototypes
- [ ] Performance meets targets
- [ ] Documentation complete
- [ ] Tag: `v1.0.0`

---

**Last Updated:** 2026-05-02  
**Status:** Framework Complete ✅
