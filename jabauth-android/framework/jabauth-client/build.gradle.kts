plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
}

// Staging target for the licence notices packaged into the AAR: the CONVENTIONAL Java
// resources dir, gitignored. Registering a build-dir source via sourceSets was tried and
// AGP silently ignored it, shipping a notice-less AAR on a green build.
// jabauth-android/{LICENSE,NOTICE} remain the single source of truth.
val licensingStageDir: File = file("src/main/resources/META-INF")

android {
    namespace = "com.jabauth.client"
    compileSdk = rootProject.property("COMPILE_SDK").toString().toInt()
    
    configurations.all {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
    
    defaultConfig {
        minSdk = rootProject.property("MIN_SDK").toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Point JNA at a host build of librabe_kem so unit tests can exercise the REAL CP-ABE
                // KEM. Unset (CI without the native lib) simply leaves the native-gated assertions
                // skipped; the wire-format assertions still run unconditionally.
                it.systemProperty("jna.library.path", System.getenv("RABE_NATIVE_DIR") ?: "")
            }
        }
    }

    // This module ships librabe_kem.so for three ABIs via src/main/jniLibs/, which AGP
    // packages into the AAR's jni/. That is MIT-licensed code (the rabe crate plus ~50
    // statically-linked crates, all permissive), and MIT requires its notice to travel
    // with every copy.
    //
    // Easy to miss: the AAR previously committed under aar-artifacts/ predates the
    // jniLibs and contains NO .so, so auditing that stale artifact shows nothing to
    // attribute. The obligation appears the moment the module is rebuilt.
    //
    // No sourceSets override: src/main/resources is already the convention, and AGP
    // packages it into classes.jar. See licensingStageDir above.
}

val stageLicensingNotices by tasks.registering(Copy::class) {
    description = "Stages LICENSE and NOTICE into the AAR's classes.jar META-INF."
    // The .txt suffix and the -jabauth-client discriminator are LOAD-BEARING: AGP's
    // default packagingOptions EXCLUDES bare META-INF/LICENSE and META-INF/NOTICE by
    // name, to avoid collisions when merging many libraries. Staging them under those
    // names produces a green build and an AAR with no notice in it.
    from(rootProject.file("LICENSE")) { rename { "LICENSE-jabauth-client.txt" } }
    from(rootProject.file("NOTICE")) { rename { "NOTICE-jabauth-client.txt" } }
    into(licensingStageDir)
}

// Hook the task that actually CONSUMES Java resources, not preBuild.
tasks.matching { it.name.startsWith("process") && it.name.endsWith("JavaRes") }
    .configureEach { dependsOn(stageLicensingNotices) }

dependencies {
    // Framework dependencies
    implementation(project(":framework:core"))
    implementation(project(":framework:jabcode-sdk"))
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.property("KOTLIN_VERSION")}")
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Cryptography
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")

    // JNA for the Rabe CP-ABE native binding (librabe_kem.so in jniLibs/<abi>/).
    // On Android, Native.load("rabe_kem", ...) resolves the lib from jniLibs
    // automatically — no classpath/temp-file fallback is required.
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    
    // JWT
    implementation("com.auth0:java-jwt:4.5.2")
    // SD-JWT VC selective-disclosure (Authlete). Java 8 bytecode, gson-only
    // compile dep (Android-native); Nimbus/junit are test-scoped in its POM and
    // are NOT pulled at runtime — no Nimbus on Android.
    implementation("com.authlete:sd-jwt:1.9")

    // Payload Format v2 — whole-body LZ4 (same lib+version as the server so a server-encoded
    // COA decodes here; LZ4 decompression is impl-independent).
    implementation("org.lz4:lz4-java:1.8.0")

    // Testing
    testImplementation("junit:junit:${rootProject.property("JUNIT_VERSION")}")
    testImplementation("org.mockito:mockito-core:${rootProject.property("MOCKITO_VERSION")}")
    testImplementation("org.robolectric:robolectric:${rootProject.property("ROBOLECTRIC_VERSION")}")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.84")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.84")
    // JVM (non-@aar) JNA so unit tests can load the host-native librabe_kem and exercise the REAL
    // CP-ABE KEM. The @aar variant above is Android-only and supplies no JVM runtime, which would
    // otherwise leave cross-party decrypt asserted rather than proven. Test scope only.
    testImplementation("net.java.dev.jna:jna:5.14.0")
    
    // Instrumented Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.google.truth:truth:1.1.5") {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
}
