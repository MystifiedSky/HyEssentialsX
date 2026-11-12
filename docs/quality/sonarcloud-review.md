# SonarCloud review — 2026-11-11

Project: `MystifiedSky_HyEssentialsX`, branch `master`. Retrieved through the SonarQube MCP connection: 20 OPEN/CONFIRMED issues (one complete 500-item page); no TO_REVIEW security hotspots. No changes to other Sonar projects.

17 findings have source fixes awaiting reanalysis. Three were marked FALSE_POSITIVE through MCP with evidence comments; no findings were accepted as a risk and no source suppression was added.

| Issue | Rule | Original file:line | Disposition |
| --- | --- | --- | --- |
| AaHNse8sHb_Ybc_X48yA | java:S2077 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/storage/MysqlStorageBackend.java:250 | Use a closed enum of fixed map-replacement statements and bind record values. |
| AaHNse8sHb_Ybc_X48yB | java:S2077 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/storage/MysqlStorageBackend.java:253 | Use a closed enum of fixed map-replacement statements and bind record values. |
| AaHNse7aHb_Ybc_X48x2 | java:S2142 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/StorageManager.java:618 | Preserve caller interrupt status in explicit InterruptedException handlers. |
| AaHNse7hHb_Ybc_X48x5 | java:S2077 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/migration/EconomySystemMigration.java:148 | Reviewed false positive: existing ASCII identifier allowlist tested against SQL syntax injection. |
| AaHNse7eHb_Ybc_X48x3 | java:S2115 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/migration/EcotaleMigration.java:276 | Reviewed false positive: legacy source credentials; restricted H2 to existing read-only files. |
| AaHNse7eHb_Ybc_X48x4 | java:S2077 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/migration/EcotaleMigration.java:320 | Reviewed false positive: existing ASCII identifier allowlist tested against SQL syntax injection. |
| AaHNse8EHb_Ybc_X48x7 | java:S2142 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/util/TeleportationUtil.java:391 | Preserve caller interrupt status in explicit InterruptedException handlers. |
| AaHNse7QHb_Ybc_X48x0 | java:S2142 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/GifManager.java:103 | Preserve caller interrupt status in explicit InterruptedException handlers. |
| AaHNse7QHb_Ybc_X48x1 | javabugs:S6466 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/GifManager.java:177 | Reset GIF metadata/cache/frame position on reload; guard model-cache bounds. |
| AaHNse55Hb_Ybc_X48xu | java:S4507 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/HologramManager.java:569 | Use structured plugin logging with causes instead of printStackTrace. |
| AaHNse55Hb_Ybc_X48xv | java:S4507 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/HologramManager.java:700 | Use structured plugin logging with causes instead of printStackTrace. |
| AaHNse7LHb_Ybc_X48xw | java:S4507 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/ImageManager.java:298 | Use structured plugin logging with causes instead of printStackTrace. |
| AaHNse7LHb_Ybc_X48xx | java:S4507 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/ImageManager.java:877 | Use structured plugin logging with causes instead of printStackTrace. |
| AaHNse7LHb_Ybc_X48xy | java:S4507 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/ImageManager.java:924 | Use structured plugin logging with causes instead of printStackTrace. |
| AaHNse7NHb_Ybc_X48xz | java:S8786 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/managers/hologram/placeholder/PlaceholderIntegration.java:17 | Replace backtracking regex with a linear delimiter scanner. |
| AaHNse8HHb_Ybc_X48x8 | java:S2142 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/util/PlaceholderApiUtil.java:105 | Preserve caller interrupt status in explicit InterruptedException handlers. |
| AaHNse8IHb_Ybc_X48x9 | java:S2142 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/util/ShopPlacementUtil.java:51 | Preserve caller interrupt status in explicit InterruptedException handlers. |
| AaHNse8gHb_Ybc_X48x- | java:S4275 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/util/ConfigManager.java:2682 | Delegate legacy rankup getter to canonical playtime-rewards getter, preserving alias behavior. |
| AaHNse8jHb_Ybc_X48x_ | java:S3923 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/util/HyFactionsUtil.java:133 | Remove conditional with identical null results. |
| AaHNse78Hb_Ybc_X48x6 | java:S1845 | src/main/java/xyz/thelegacyvoyage/hyessentialsx/commands/importer/ImportHomesCommand.java:248 | Rename Java member and preserve legacy worldUUID spelling with SerializedName. |

## Validation

- `gradlew.bat compileJava test --console=plain`: successful; 22 tests, zero failures. Added 10 focused tests across interruption, import aliases, SQL maps, H2 read-only/file safety, and placeholder detection.
- Windows Java NIO initially failed to establish its internal loopback connection. Successful process-local invocation used Java 21 for Gradle, Java 25 toolchain for compilation/tests, and `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp`. No persisted setting was changed.
- Existing Hytale deprecation warnings remain; they were not in the retrieved Sonar issue set.
- GIF rendering and live reload were reviewed in source but not exercised in a running Hytale server.
- No Sonar scanner/analyzer execution tool is exposed by this MCP connection. A fresh normal SonarCloud analysis is required to establish remaining findings after these uncommitted changes; source fixes are not reported as cloud-verified closures.
- No commit, push, plugin deployment, or runtime data edits were performed.

## Reviewed SQL cases

Ecotale and TheEconomy table identifiers already pass validators permitting only ASCII letters, digits, and underscore, with a fixed safe fallback. JDBC value placeholders cannot bind identifiers. Tests cover quotes, backticks, whitespace, comments, semicolons, and Unicode.

Ecotale's existing embedded H2 file uses its source plugin's legacy credentials. Setting a new password would break access to that third-party data. The importer now uses `IFEXISTS=TRUE;ACCESS_MODE_DATA=r;AUTO_SERVER=FALSE`; tests confirm writes are refused, source bytes remain unchanged, and missing files are never created. The source file's filesystem permissions remain its access boundary. See [H2 file access documentation](https://h2database.github.io/html/features.html#custom_access_mode).

