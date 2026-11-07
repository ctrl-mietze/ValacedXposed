# Kompatibilität und Diagnose

ValancedX ist **kein Universal-Hook** für alle Discord-Versionen. React Native, Hermes, interne APIs und obfuskierte Klassen können sich zwischen Discord-Versionen ändern.

## Was die Statusanzeige aussagt

- **Noch keine Hook-Rückmeldung:** Es wurde noch kein Status aus dem Discord-Prozess empfangen. Das beweist weder, dass das Framework aktiv ist, noch dass es inaktiv ist.
- **Native Hooks registriert:** Die Bootstrap-Reihenfolge lief durch. Einzelne Tweaks können eigene Warnungen protokolliert haben.
- **Bundle zuletzt erfolgreich geladen:** Der Script-Loader hat das Bundle ohne gemeldete Ladeexception ausgeführt. Das garantiert nicht, dass alle Plugins funktionieren.
- **Native-Modus ohne JavaScript:** Der Nutzer hat das Hermes-Bundle deaktiviert.
- **Hook-Fehler gemeldet:** Die lokale Diagnose enthält eine Fehlermeldung, soweit aktiviert.

Die Werte zeigen den **letzten** beobachteten Start, keine permanente Echtzeitverbindung. Der Zeitstempel ist deshalb wichtiger als eine vermeintliche Live-Anzeige.

## Stabilität

Das Modul ist strikt auf den Discord-Hauptprozess beschränkt. Der Script-Injector hat einen Prozess-Guard gegen doppelte Ausführung. Fehler beim Initialisieren werden isoliert, nicht als erwiesene universelle Kompatibilität ausgegeben.

## Testablauf

1. Debug-APK installieren und in LSPosed den Scope `com.discord` setzen.
2. Discord erzwingen-beenden und neu öffnen.
3. Im Manager den Hook-Status prüfen.
4. `logcat` mit dem Tag `ValancedX` und Framework-Logs auswerten.
5. Bei Discord-Updates erst den Hook auf einem Testgerät prüfen und bei Problemen Hermes-Injektion deaktivieren.

Keine fremden Zugangsdaten sammeln, verändern oder exportieren.
