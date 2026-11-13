# Kniejapunkty — punktacja zastępów

Aplikacja: https://mikoorty.github.io/Knieja-punkty/
Aplikacja Android: https://github.com/mikoorty/Knieja-punkty/releases/latest/download/Kniejapunkty.apk

- Dane i logowanie: Firebase (projekt `kniejapunkty`)
- Reguły bezpieczeństwa bazy: `firestore.rules` (wklejone w konsoli Firebase → Firestore → Rules)
- Logika punktacji: `scoring.js`
- Kody QR: generowanie i etykietowanie przez kadrę (do druku / pobrania), skanowanie przez harcerzy (aparat na żywo / plik), powiadomienia i rejestr skanów w czasie rzeczywistym dla kadry
