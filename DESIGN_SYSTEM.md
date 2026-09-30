# PY4U interface system

The app uses `IdeDesignSystem.kt` for shared Compose colors, typography, shapes, navigation, cards, sheets, actions and empty states. The concepts guide layout and hierarchy; neon glows and continuous shaders are omitted.

| Role | Value |
| --- | --- |
| Background | `#0D1118` |
| Surface | `#171D28` |
| Raised surface | `#222B39` |
| Highest surface | `#2C3748` |
| Outline | `#3A4657` |
| Primary text | `#F0F1F3` |
| Secondary text | `#AAB8CB` |
| Default accent | `#78ADFF`, customizable |
| Error, warning, success | `#F0ABA8`, `#E4C28A`, `#AACDB4` |

Typography uses Android sans serif for chrome and the user's editor font choice for code. Headings are 24–30 sp, controls 14–16 sp, secondary labels 11–12 sp. Spacing uses 8, 12, 16, 20 and 24 dp. Compact controls have 10 dp corners, cards 16 dp, and sheets 24 dp top corners. Toolbar icons are 24 dp.

Material 3 supplies focus, press, disabled, keyboard and accessibility behavior for buttons, inputs, menus, dialogs and navigation. Press feedback scales cards to 0.98 with a high-stiffness spring. Timing tokens are 120 ms for quick feedback, 200 ms for standard changes and 240 ms for entrances. Animations respect the system animator setting. Native editor text, cursor, syntax and console streaming remain outside expensive Compose effects. Existing appearance controls and saved projects are retained.

Major surfaces: Home and file browsing use searchable project cards; Code uses compact tabs and controls; Console emphasizes output and input; Astro uses restrained conversation cards; Settings uses category navigation; file actions, tabs, tools and packages use consistent bottom sheets. Empty, error and loading states use the same surface and type hierarchy.

The refreshed direction adds a distinct current-workspace panel, color-backed action tiles, stronger selected tabs, section markers, and a restrained accent rail across navigation and settings. It uses one user-selected accent and tinted neutral surfaces without glows.

The Library separates installed distributions, modules actually importable by the embedded Python runtime, and PyPI. The PyPI tab explicitly syncs the official Simple Index project names into a local SQLite catalog. Search reads a bounded prefix from that catalog; installs still use the existing compatibility-checked runtime installer. No curated list is presented as the full index. The catalog is optional because the complete index is a large network and storage download.
