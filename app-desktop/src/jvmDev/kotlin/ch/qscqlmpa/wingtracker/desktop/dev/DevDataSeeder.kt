package ch.qscqlmpa.wingtracker.desktop.dev

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.desktop.DataDirectory
import ch.qscqlmpa.wingtracker.desktop.appModules
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Entry point of `./gradlew seedDevData`: resets the development data folder and fills it with sample data.
 * It only ever touches a folder whose name ends in "-dev", so the real data of the application is safe.
 */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
fun main() {
    val directory = DataDirectory.resolve(devMode = true)
    reset(directory)
    val koin = koinApplication { modules(appModules(directory)) }.koin
    val summary = seedDevData(koin, directory)
    println("Sample data written to $directory")
    println(summary)
}

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
internal fun reset(directory: Path) {
    check(directory.name.endsWith("-dev")) { "Refusing to reset '$directory': it is not a development data folder" }
    if (directory.exists()) directory.deleteRecursively()
    Files.createDirectories(directory)
}

/** Fills the data folder behind [koin] with sample data and returns a short description of what was created. */
fun seedDevData(
    koin: Koin,
    directory: Path,
): String {
    val today = koin.get<AppClock>().today()
    val seeder = SampleDataSeeder(koin, directory, today)
    return kotlinx.coroutines.runBlocking { seeder.seed() }
}
