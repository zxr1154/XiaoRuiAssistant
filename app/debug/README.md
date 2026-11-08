This directory contains the public development signing key for XiaoRuiAssistant Debug builds.

The alias is `androiddebugkey`; the standard Android development passwords are `android`.
Only the Debug signing configuration uses this key. Release builds continue to require their separate private signing configuration.
Keep this development key stable so future Debug APKs can update an existing development installation.

The upgrade identity is `io.github.mangi.xiaorui.assistant` with certificate SHA-256
`d1eac42aa52ea61f364542b9f8207775aad0a6b44aa50f8e281086fdbcddd77d`.
Do not regenerate or replace this key, and increment `versionCode` for every distributed update.
Verify this certificate fingerprint before distributing APKs. A Release APK signed with a different
certificate must not be offered as an in-place update for these installations.
