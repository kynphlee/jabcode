# Android Custom Framework and AAR Design Best Practices

Designing a custom Android framework for distribution as an Android Archive (AAR) requires careful consideration of architecture, API design, dependency management, and publishing strategies. This report synthesizes insights and best practices from official Android documentation, Jetpack library guidelines, and community standards to guide the development of robust, maintainable, and developer-friendly Android libraries.

## 1. Module Architecture and AAR Fundamentals

An Android library compiles into an AAR file, which differs from a standard JAR by including Android resources (layouts, drawables, strings) and an Android manifest file [1]. 

### Modularization Strategy
When designing a framework, adopting a multi-module architecture improves maintainability and scalability. The official Android architecture guidelines recommend separating concerns into distinct module types [2]:
*   **Data Modules:** Encapsulate data handling, business logic, and expose repositories as external APIs while hiding implementation details (e.g., local databases or network sources).
*   **Feature Modules:** Isolate specific functionalities or screens. These modules depend on data modules but should remain decoupled from other feature modules.
*   **Common/Core Modules:** Contain shared code such as UI widgets, analytics trackers, or network clients to reduce redundancy.
*   **Abstraction Modules:** Define the API contract (interfaces and models) that other modules depend on, enabling dependency inversion and swapping concrete implementations without affecting consumers.

### Resource Management and Conflicts
When an AAR is consumed by an app, its resources are merged with the app's resources. If a resource name in the library matches one in the app, the app's resource takes precedence, potentially breaking the library's UI [1]. 
*   **Resource Prefixing:** To prevent naming collisions, libraries must use a consistent prefix for all resources. This can be enforced in Gradle using the `resourcePrefix` property [3].
*   **Public vs. Private Resources:** By default, all resources in a library are public. To protect internal resources and prevent consumers from relying on them, explicitly declare public resources in a `res/values/public.xml` file. If no resources should be public, include an empty `<public />` tag to make all resources implicitly private [1].

## 2. API Design and Binary Compatibility

A framework's API is its contract with consumers. Maintaining stability and clear versioning is critical for adoption.

### Semantic Versioning and Compatibility
Android libraries should follow strict semantic versioning (Major.Minor.Patch) to communicate compatibility changes [4]:
*   **Source Compatibility (API):** Changes do not break the consumer's source code when recompiling.
*   **Binary Compatibility (ABI):** A newer version of the library can replace an older version at runtime without causing linkage errors (e.g., `NoSuchMethodError`). Major version bumps indicate breaking ABI changes [5].

### API Design Guidelines
Following the Android API Council guidelines ensures consistency with the broader Android ecosystem [6]:
*   **Prefer Interfaces over Abstract Classes:** For stateless contracts, use interfaces with default methods (requiring Java 8+). Use abstract classes only when internal state or constructors are required.
*   **Avoid Exposing Implementation Details:** Do not expose raw Binder objects or IDL-generated code (like AIDL) directly. Wrap them in manager classes to allow future evolution of the IPC interface without breaking the public API.
*   **Manager Classes:** Classes that act as the primary interaction point with system services should be declared as `final`.
*   **Asynchronous APIs:** Prefer a combination of completion callbacks, `Executor`, and `CancellationSignal` over `CompletableFuture` or `Future` for low-level APIs.

### Binary Compatibility Validation
To prevent accidental breaking changes, library authors should use the **Binary Compatibility Validator** plugin. This tool generates an API dump (`.api` file) of the public ABI. During CI, it checks if code changes alter the public ABI, ensuring that internal refactoring does not inadvertently break binary compatibility [5].

## 3. Dependency Management and Optimization

How a library manages its dependencies directly impacts the consuming application's build time, size, and stability.

### Dependency Configurations
Use the correct Gradle dependency configurations to control what is exposed to consumers [7]:
*   `api`: Use for dependencies that are part of the library's public API. Consumers of the library will transitively receive these dependencies.
*   `implementation`: Use for internal dependencies. These are hidden from the consumer's compile classpath, improving build times and preventing consumers from accidentally relying on the library's internal dependencies.
*   `compileOnly`: Use for dependencies required at compile time but provided by the consumer at runtime.

### Bill of Materials (BOM) and Version Catalogs
For frameworks consisting of multiple artifacts, provide a Bill of Materials (BOM). A BOM allows consumers to specify a single version for the entire framework, ensuring that all included modules are compatible with each other [8]. Internally, manage dependencies using Gradle Version Catalogs to centralize version definitions.

### ProGuard and R8 Optimization
Libraries must be compatible with Android's R8 optimizer without requiring manual configuration from the consumer [9].
*   **Consumer Keep Rules:** Bundle necessary ProGuard rules within the AAR using the `consumerProguardFiles` property. These rules are automatically applied when the app is optimized.
*   **Targeted Rules:** Avoid broad, package-wide keep rules (e.g., `-keep class com.mylibrary.** { *; }`). Instead, write targeted rules only for classes accessed via reflection or JNI.
*   **Prefer Code Generation:** Use code generation (e.g., KSP) over reflection whenever possible, as it is more compatible with R8 and allows for better dead-code stripping.

## 4. Publishing and Distribution

Distributing AAR files manually (e.g., via email or direct download) strips away critical metadata such as versioning and transitive dependencies. Libraries must be published to a Maven repository [10].

### Maven Publish Plugin
Use the Gradle Maven Publish Plugin to generate the publication. This process creates the AAR along with a POM file that declares the library's identity (groupId, artifactId, version) and its dependency graph [10].

### AAR Metadata and Manifest Merging
*   **minCompileSdk:** Specify `minCompileSdk` in the `aarMetadata` block to indicate the minimum `compileSdk` required by consuming projects [11].
*   **Manifest Merging:** The library's `AndroidManifest.xml` is merged into the consuming app's manifest. Avoid defining `<uses-sdk>` in the manifest; rely on the `build.gradle` configuration instead. Be cautious when declaring permissions, as they will be silently added to the consuming app [12].

### Documentation
Comprehensive documentation is essential. Use KDoc for Kotlin codebases and generate HTML documentation using Dokka. Ensure that the generated documentation JAR is attached to the Maven publication so that it is accessible within the consumer's IDE.

---

## References

[1] Create an Android library. Android Developers. https://developer.android.com/studio/projects/android-library
[2] Common modularization patterns. Android Developers. https://developer.android.com/topic/modularization/patterns
[3] Android library development best practices guide. OCTO Talks. https://blog.octo.com/android-library-development-best-practices-guide
[4] AndroidX releases. Android Developers. https://developer.android.com/jetpack/androidx/versions
[5] Android library compatibility and public API management with Binary Compatibility Validator. ProAndroidDev. https://proandroiddev.com/android-library-compatibility-and-public-api-management-with-binary-compatibility-validator-971762957594
[6] Android API guidelines. Android Open Source Project. https://source.android.com/docs/setup/contribute/api-guidelines
[7] Difference Between implementation and compile in Gradle. Baeldung. https://www.baeldung.com/gradle-implementation-vs-compile
[8] Use a Bill of Materials. Android Developers. https://developer.android.com/develop/ui/compose/bom
[9] Optimization for library authors. Android Developers. https://developer.android.com/topic/performance/app-optimization/library-optimization
[10] Upload your library. Android Developers. https://developer.android.com/build/publish-library/upload-library
[11] Prepare your library for release. Android Developers. https://developer.android.com/build/publish-library/prep-lib-release
[12] Manage manifest files. Android Developers. https://developer.android.com/build/manage-manifests
