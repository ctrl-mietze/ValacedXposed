# ValacedXposed

Ein eigenständiger Android-Xposed-Fork für Discord. Die installierte App heißt **ValancedX** und ist gleichzeitig Modul und Manager.

![ValancedX](assets/logo.svg)

## Was das Projekt macht

ValancedX wird über ein kompatibles Xposed-Framework direkt in die offizielle Discord-App eingebunden. Es erstellt **keine** gepatchte Discord-APK und benötigt keinen zusätzlichen Discord-Client.

Der native Loader, die Plugin-Bridge und das Hermes-Bundle bilden weiterhin eine zusammengehörige technische Einheit. Die Manager-App ist neu gestaltet und arbeitet vollständig lokal. Sie bietet:

- Einen Hauptschalter für die Hook-Initialisierung.
- Eine separate Freigabe für das integrierte Hermes-Bundle.
- Eine letzte Hook-Rückmeldung mit Zeitstempel und optionalen Fehlerdetails.
- Discord-Schnellstart, Kopierfunktion für Diagnosen und eine kurze Xposed-Einrichtungshilfe.
- Einen engeren Prozess-Scope, einmalige Script-Injektion und isolierte Hook-Initialisierung.

## Installation

1. Baue oder lade eine APK aus **diesem Projekt** und installiere sie.
2. Aktiviere ValancedX im Xposed-Manager (zum Beispiel LSPosed).
3. Wähle `com.discord` als einzige Ziel-App.
4. Stoppe Discord vollständig und starte es neu.
5. Öffne ValancedX, um die letzte Rückmeldung des Hooks zu sehen.

Die Kompatibilität hängt von Discord-Version, Framework und Hermes-Bundle ab. Eine erfolgreiche Installation garantiert nicht, dass jede Discord-Version unterstützt wird. **Ein Build oder Release ist keine Zusage einer geprüften Discord-Kompatibilität.**

## Build

Voraussetzungen: Java 25, Android SDK 37, Deno 2.9.7 sowie ein Android-kompatibles Gradle-Setup.

```bash
./gradlew :android:assembleDebug :android:testDebugUnitTest :bundle:testBundle
```

Die APK entsteht unter `android/build/outputs/apk/debug/`. Release-Versionen erhalten später eine eigene Signierung.

## Herkunft und Lizenz

Dieses Projekt basiert auf [Valenced/valenced](https://github.com/Valenced/valenced). Der verwendete Quellcode bleibt unter der GNU General Public License v3.0; die ursprünglichen Rechte und Lizenzen sind in den entsprechenden Lizenzdateien erhalten.

Name, Logo, Manager-Oberfläche, Projektbeschreibung und die hier beschriebenen Ergänzungen wurden eigens für ValacedXposed erstellt. Das Projekt ist nicht offiziell mit Discord oder Valenced verbunden.

Weitere technische Einzelheiten: [Architektur](docs/ARCHITECTURE.md) · [Kompatibilität und Diagnose](docs/COMPATIBILITY.md)
