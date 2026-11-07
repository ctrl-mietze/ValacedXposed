# ValacedXposed

Ein eigenstÃ¤ndiger Android-Xposed-Fork fÃ¼r Discord. Die installierte App heiÃŸt **ValancedX** und ist gleichzeitig Modul und Manager.

![ValancedX](assets/logo.svg)

## Was das Projekt macht

ValancedX wird Ã¼ber ein kompatibles Xposed-Framework direkt in die offizielle Discord-App eingebunden. Es erstellt **keine** gepatchte Discord-APK und benÃ¶tigt keinen zusÃ¤tzlichen Discord-Client.

Der native Loader, die Plugin-Bridge und das Hermes-Bundle bilden weiterhin eine zusammengehÃ¶rige technische Einheit. Die Manager-App ist neu gestaltet und arbeitet vollstÃ¤ndig lokal. Sie bietet:

- Einen Hauptschalter fÃ¼r die Hook-Initialisierung.
- Eine separate Freigabe fÃ¼r das integrierte Hermes-Bundle.
- Eine letzte Hook-RÃ¼ckmeldung mit Zeitstempel und optionalen Fehlerdetails.
- Discord-Schnellstart, Kopierfunktion fÃ¼r Diagnosen und eine kurze Xposed-Einrichtungshilfe.
- Einen engeren Prozess-Scope, einmalige Script-Injektion und isolierte Hook-Initialisierung.

## Installation

1. Baue oder lade eine APK aus **diesem Projekt** und installiere sie.
2. Aktiviere ValancedX im Xposed-Manager (zum Beispiel LSPosed).
3. WÃ¤hle `com.discord` als einzige Ziel-App.
4. Stoppe Discord vollstÃ¤ndig und starte es neu.
5. Ã–ffne ValancedX, um die letzte RÃ¼ckmeldung des Hooks zu sehen.

Die KompatibilitÃ¤t hÃ¤ngt von Discord-Version, Framework und Hermes-Bundle ab. Eine erfolgreiche Installation garantiert nicht, dass jede Discord-Version unterstÃ¼tzt wird. **Ein Build oder Release ist keine Zusage einer geprÃ¼ften Discord-KompatibilitÃ¤t.**

## Build

Voraussetzungen: Java 25, Android SDK 36, Deno 2.9.7 sowie ein Android-kompatibles Gradle-Setup.

```bash
./gradlew :android:assembleDebug :android:testDebugUnitTest :bundle:testBundle
```

Die APK entsteht unter `android/build/outputs/apk/debug/`. Release-Versionen erhalten spÃ¤ter eine eigene Signierung.

## Herkunft und Lizenz

Dieses Projekt basiert auf [Valenced/valenced](https://github.com/Valenced/valenced). Der verwendete Quellcode bleibt unter der GNU General Public License v3.0; die ursprÃ¼nglichen Rechte und Lizenzen sind in den entsprechenden Lizenzdateien erhalten.

Name, Logo, Manager-OberflÃ¤che, Projektbeschreibung und die hier beschriebenen ErgÃ¤nzungen wurden eigens fÃ¼r ValacedXposed erstellt. Das Projekt ist nicht offiziell mit Discord oder Valenced verbunden.

Weitere technische Einzelheiten: [Architektur](docs/ARCHITECTURE.md) Â· [KompatibilitÃ¤t und Diagnose](docs/COMPATIBILITY.md)

