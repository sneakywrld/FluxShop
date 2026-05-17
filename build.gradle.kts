import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar


plugins {
    java
    id("io.github.goooler.shadow") version "8.1.8"
    `maven-publish`
}

group = "dev.fluxshop"
version = "1.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    // Paper / Spigot
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    // PlaceholderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // Citizens
    maven("https://maven.citizensnpcs.co/repo")
    // MythicMobs
    maven("https://mvn.lumine.io/repository/maven-public/")
    // DiscordSRV
    maven("https://nexus.scarsz.me/content/groups/public")
    // LuckPerms
    maven("https://repo.lucko.me/")
    // WorldGuard / WorldEdit
    maven("https://maven.enginehub.org/repo/")
    // RoseStacker
    maven("https://repo.rosewooddev.io/repository/public/")
    // WildStacker
    maven("https://repo.bg-software.com/repository/api/")
    // UltimateStacker (bg-software)
    maven("https://repo.bg-software.com/repository/api/")
    // JitPack (ItemsAdder, Oraxen, various others)
    maven("https://jitpack.io")
    // Vault
    maven("https://jitpack.io")
}

dependencies {
    // ─── Server API ───────────────────────────────────────────────────────────
    // Compile against Paper 1.21 for full API; NMS layer handles older versions
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // ─── Economy ──────────────────────────────────────────────────────────────
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    // Flux API — local project reference (run `./gradlew publishToMavenLocal` in the Flux project first,
    // OR point this at the built JAR: files("../Flux/flux-api/build/libs/flux-api-1.0.0.jar"))
    compileOnly(files("../Flux/flux-api/build/libs/flux-api-1.0.0.jar"))

    // ─── Utilities ────────────────────────────────────────────────────────────
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.discordsrv:discordsrv:1.27.0")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")

    // ─── NPC ──────────────────────────────────────────────────────────────────
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }

    // ─── Mobs & Custom Items ──────────────────────────────────────────────────
    compileOnly("io.lumine:Mythic-Dist:5.7.0-SNAPSHOT")
    // ItemsAdder and Oraxen are accessed entirely via reflection at runtime;
    // no compile-time dep needed (avoids repo resolution failures).

    // ─── Spawner Plugins ──────────────────────────────────────────────────────
    // All spawner plugins (RoseStacker, WildStacker, SilkSpawners, UltimateStacker,
    // SmartSpawner, EpicSpawners, MineableSpawners) are accessed via reflection at
    // runtime to avoid compile-time deps and API version fragility.

    // ─── Shaded Dependencies ──────────────────────────────────────────────────
    // HikariCP for database connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")
    // Caffeine for in-memory caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    // bStats metrics
    implementation("org.bstats:bstats-bukkit:3.0.2")

    // ─── Test Dependencies ────────────────────────────────────────────────────
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    // Paper API available in tests for mocking Bukkit types (not shaded — test only)
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // SQLite JDBC driver for DatabaseSchemaTest (shaded in prod JAR but needed in test classpath)
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        archiveFileName.set("FluxShop-${project.version}.jar")

        relocate("com.zaxxer.hikari",   "dev.fluxshop.libs.hikari")
        relocate("com.github.benmanes", "dev.fluxshop.libs.caffeine")
        relocate("org.bstats",          "dev.fluxshop.libs.bstats")

        // Exclude signatures and unnecessary META-INF entries
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("module-info.class")
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false // use shadowJar only
    }
}

// ── API JAR (developer dependency via JitPack) ────────────────────────────────
// Contains only dev.fluxshop.api.** — events and the FluxShopAPI facade.
// Consumers add: compileOnly("com.github.sneakywrld:FluxShop:VERSION:api")
val apiJar by tasks.registering(Jar::class) {
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("dev/fluxshop/api/**")
    }
    dependsOn(tasks.compileJava)
}

publishing {
    publications {
        create<MavenPublication>("api") {
            groupId   = project.group.toString()
            artifactId = "fluxshop-api"
            version   = project.version.toString()
            artifact(apiJar)

            pom {
                name.set("FluxShop API")
                description.set("Developer API for the FluxShop Minecraft plugin — events and service facades.")
                url.set("https://github.com/sneakywrld/FluxShop")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
