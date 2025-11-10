# Diagnostic Report: JABCode Native Loader Failure (HiCAP Fountain)

- **Component**: `HiCapFountain` (`src/main/java/com/nexus/qrforge/service/fountain/HiCapFountain.java`)
- **Library**: `com.jabcode:jabcode-java:1.0.0`
- **Error window**: 2025-10-22
- **Runtime**: Spring Boot 3.1.0, Java 21, Linux

## Summary
Attempting to stream HiCAP (JABCode) frames fails immediately at first encode with a native loader error from JABCode’s shaded JavaCPP stack. Status messages over STOMP succeed (start/stop), but no frames are emitted to `/queue/fountain-stream` because `HiCapFountain` returns an empty selection after the exception.

## Impact
- **User-visible**: HiCAP page shows no QR frames; only start/stop confirmations on `/topic/fountain-stream`.
- **System**: Fountain worker thread logs an exception and continues without generating images.

## Error details (stack excerpt)
```
Error putting member offsets for class com/jabcode/internal/JABCodeNative$jab_data.
Exception in thread "pool-3-thread-2" java.lang.ExceptionInInitializerError
  at com.jabcode.internal.bytedeco.javacpp.Loader.load(Loader.java:1289)
  at com.jabcode.internal.JABCodeNativePtr.<clinit>(JABCodeNativePtr.java:17)
  at com.jabcode.OptimizedJABCode.encode(OptimizedJABCode.java:241)
  at com.jabcode.core.JABCode.encode(JABCode.java:201)
  at ... HiCapFountain$HiCapSelector.processSelection(HiCapFountain.java:118)
Caused by: java.lang.ClassCastException: class com.jabcode.internal.JABCodeNative$jab_data
  at org.bytedeco.javacpp.Loader.putMemberOffset(Loader.java:1997)
  ...
```

## Findings
- **[shaded vs. unshaded JavaCPP conflict]** The stack includes both `com.jabcode.internal.bytedeco.javacpp.Loader` (shaded) and `org.bytedeco.javacpp.Loader` (unshaded). The `ClassCastException` occurs because the unshaded loader tries to treat `com.jabcode.internal.JABCodeNative$jab_data` as a subclass of `org.bytedeco.javacpp.Pointer`. It isn’t—it's a subclass of the shaded `com.jabcode.internal.bytedeco.javacpp.Pointer`. This mismatch is a hallmark of having both shaded and unshaded JavaCPP classes on the classpath.
- **[Project POM]** `pom.xml` does not explicitly add `org.bytedeco:*`. A transitive dependency (likely `com.nexus:qrforge-lib:1.0-SNAPSHOT` or another) may be pulling `org.bytedeco:javacpp` or a `*-platform` artifact into the runtime classpath.
- **[JVM/classloader issues]** Spring Boot DevTools was previously removed to avoid duplicate classloaders. The current error signature is not a DevTools restart loader issue; it is a package mismatch between shaded and unshaded JavaCPP.

## Root cause hypothesis
- Primary: A transitive dependency brings unshaded `org.bytedeco.javacpp` onto the classpath, causing JABCode’s shaded loader to interoperate with the wrong `Loader`/`Pointer` types, triggering `ClassCastException` during native struct layout computation.
- Secondary: JABCode 1.0.0’s internal JavaCPP version may be older and fragile on newer JDKs. Even without a second JavaCPP, Java 21 can expose loader layout quirks—but the mixed shaded/unshaded classes strongly suggest a dependency conflict first.

## Verification steps
1. **Inspect dependencies**
   - Run: `mvn -q dependency:tree | grep -i javacpp`
   - If any `org.bytedeco:javacpp` or `org.bytedeco:*:*-platform` entries exist, note which parent dependency brings them in.
2. **Search for JavaCPP in code/resources**
   - Run: `mvn -q dependency:tree | grep -i bytedeco`
   - Check if your internal libraries (e.g., `qrforge-lib`) declare JavaCPP.
3. **Enable loader debug logs (optional)**
   - Add JVM arg: `-Dorg.bytedeco.javacpp.logger.debug=true`
   - Because JABCode shades JavaCPP, also try: `-Dcom.jabcode.internal.bytedeco.javacpp.logger.debug=true`

## Mitigations within this project
- **[Exclude conflicting JavaCPP]**
  - Identify the dependency which brings `org.bytedeco:javacpp` and add an `<exclusions>` block. Example pattern:
    ```xml
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>qrforge-lib</artifactId>
      <version>1.0-SNAPSHOT</version>
      <exclusions>
        <exclusion>
          <groupId>org.bytedeco</groupId>
          <artifactId>javacpp</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.bytedeco</groupId>
          <artifactId>*-platform</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    ```
  - The goal is to ensure only JABCode’s shaded `com.jabcode.internal.bytedeco.*` classes are present at runtime.

- **[Graceful fallback in HiCAP]**
  - Keep user experience unblocked: if JABCode encode fails, fall back to ZXing in `HiCapFountain.processSelection(...)` to generate a standard mono QR image via the existing `Serializer`. This preserves streaming on `/queue/fountain-stream` while you resolve the native conflict.

- **[Feature flag]**
  - Add a configuration toggle to disable HiCAP generation at runtime (e.g., `odf.hicap.enabled=false`) so environments that lack proper native setup default to Sequential.

## Longer-term options
- **Align on one JavaCPP**: If `org.bytedeco` is a hard requirement elsewhere, consider requesting an unshaded JABCode build or a JABCode release that avoids internal relocation conflicts.
- **JDK/version note**: If the conflict persists even after exclusions, validate JABCode 1.0.0 with Java 21 on your platform; open an upstream ticket with the stack, OS, and JDK details.

## Current codepaths affected
- `HiCapFountain$HiCapSelector.processSelection()` → `JABCode.encode(...)` throws and returns an empty selection
- `HiCapFountain.executeStep()` sees empty selection and does not call `frameListener.frameDataLoaded(...)`
- `SimpleFountainPool.setFrameListener(...)` never receives a frame for HiCAP, hence no `/queue/fountain-stream` messages

## Recommended next actions (for this project)
1. Run dependency checks to locate `org.bytedeco` artifacts and exclude them.
2. Implement a ZXing fallback in `HiCapFountain` to maintain streaming during investigation.
3. Add a runtime toggle to disable HiCAP if needed.
4. Re-test: start HiCAP, wait ~1–2s before stop; confirm frames appear.
5. If still failing, capture debug logs with the JavaCPP debug properties and file an upstream issue to JABCode with environment specs and this stack trace.

## Appendix: Environment
- Spring Boot 3.1.0
- Java 21 (pom.xml)
- OS: Linux (host)
- Dependencies relevant:
  - `com.jabcode:jabcode-java:1.0.0` (shaded JavaCPP at `com.jabcode.internal.bytedeco`)
  - `com.google.zxing:core/javase:3.5.2`
  - Potential transitive `org.bytedeco:*` (to be confirmed via dependency:tree)

