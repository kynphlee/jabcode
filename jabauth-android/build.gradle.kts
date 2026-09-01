// Top-level build file for JABAuth Android

plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
}

// Shared configuration for all subprojects
subprojects {
    // Apply common configurations after project evaluation
    afterEvaluate {
        // Configure Maven publishing for library modules
        if (project.plugins.hasPlugin("com.android.library")) {
            apply(plugin = "maven-publish")
            
            // Configure publishing after Android plugin creates components
            project.afterEvaluate {
                configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("release") {
                            from(components["release"])
                            
                            groupId = "com.jabauth.framework"
                            artifactId = project.name
                            version = rootProject.property("FRAMEWORK_VERSION").toString()
                            
                            pom {
                                name.set("JABAuth ${project.name}")
                                description.set("JABAuth Framework - ${project.name} module")
                                url.set("https://github.com/kynphlee/jabcode")

                                // PROPRIETARY, not MIT. This previously declared "MIT License"
                                // while the repository's only LICENSE file was LGPL-2.1 — a
                                // machine-readable claim contradicting the file it pointed at,
                                // riding along with an AAR that carries three native libraries
                                // and no notice at all.
                                //
                                // The repository root is now MIT (see the root LICENSE for why
                                // the LGPL text was stale), but this subtree is expressly carved
                                // OUT of that grant: it is the commercial SDK. The MIT-licensed
                                // codec it embeds is attributed in NOTICE, which ships in the AAR.
                                licenses {
                                    license {
                                        name.set("Proprietary — All Rights Reserved")
                                        comments.set(
                                            "Proprietary to Kendall Fleming. The embedded JAB Code " +
                                            "native libraries are MIT-licensed; see NOTICE in the AAR."
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    repositories {
                        // Local Maven repository for development
                        maven {
                            name = "Local"
                            url = uri("${rootProject.layout.buildDirectory.get().asFile}/maven-repo")
                        }
                        
                        // Uncomment for production publishing to GitHub Packages
                        // maven {
                        //     name = "GitHubPackages"
                        //     url = uri("https://maven.pkg.github.com/yourorg/jabauth-android")
                        //     credentials {
                        //         username = System.getenv("GITHUB_ACTOR")
                        //         password = System.getenv("GITHUB_TOKEN")
                        //     }
                        // }
                    }
                }
            }
        }
    }
}

// Composite JaCoCo report for all modules
tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Generate combined code coverage report for all modules"
    
    dependsOn(subprojects.map { it.tasks.withType<Test>() })
    
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
        
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoRootReport/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoRootReport/jacocoRootReport.xml"))
    }
    
    // Collect source and class files from Android library modules
    val sourceDirs = subprojects.flatMap { project ->
        project.file("src/main/java").takeIf { it.exists() }?.let { listOf(it) } ?: emptyList()
    }
    
    val classDirs = subprojects.flatMap { project ->
        val debugClasses = project.file("build/tmp/kotlin-classes/debug")
        debugClasses.takeIf { it.exists() }?.let { listOf(it) } ?: emptyList()
    }
    
    sourceDirectories.setFrom(files(sourceDirs))
    classDirectories.setFrom(files(classDirs))
    
    executionData.setFrom(
        files(subprojects.map { project ->
            project.layout.buildDirectory.get().asFile.resolve("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        }.filter { it.exists() })
    )
}

// Clean task
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
