# CTG Hall - Android TV app for ctghall.com/Data/

A Netflix-style front end for the nginx directory server at https://ctghall.com/Data/.
It reads the folder pages, shows movies and series as poster cards (posters, backdrops and descriptions from TMDB),
and plays the files directly with ExoPlayer. It installs next to the other CTG app (different package name).

## Get the APK (no Android Studio needed)

1. On https://github.com make a **new repository** (for example `CTG-Hall`). Do not reuse the old one.
2. Upload everything from this folder into it, **including the hidden `.github` folder**
   (Add file > Upload files, drag the folder contents in).
   If `.github/workflows/build.yml` did not upload, use Add file > Create new file, type
   `.github/workflows/build.yml` as the name and paste the contents of that file.
3. (Optional) Settings > Secrets and variables > Actions > New repository secret named `TMDB_API_KEY`
   to bake your TMDB key into the APK. You can also type it in the app under Settings.
4. Open the **Actions** tab. "Build APK" runs by itself (about 3-5 minutes). If not, press "Run workflow".
5. When it is green, open the run, scroll to **Artifacts** at the bottom and download **CtgHall-APK**
   (a zip with `app-debug.apk`). Install it on the TV.

If the build fails, open the failed step, copy the red error lines and send them to Claude.

## Using it

- **Home**: big backdrop and description of whatever poster is highlighted, then rows. Default rows are
  `Movies/English/2026/` (newest first) and `TV-Series/Japanese-Anime-Animated/`.
  "Recently watched" appears after you play something, with a progress bar on each poster.
- **Movies / TV Series / Videos / All files** (top bar): folder tiles to go deeper, poster grids for titles.
  Use **Filter** to narrow a long list, **Sort** for A-Z or Newest, **Add to Home** to pin a folder as a Home row.
- **Series**: choose a season at the top, pick an episode. The rest of the season plays on automatically.
- **Subtitles**: `.srt` / `.vtt` / `.ass` next to the video and inside `Subs/` are loaded. In the player use the
  subtitle button to switch them on. Settings has "Subtitles on by default".
- **Player**: Left/Right = seek 10 s (controls hidden), OK = show controls / pause, Back = exit. Position is saved.
- Back returns to the exact poster you left.

## Limits

- The TV must be able to reach ctghall.com.
- Some TVs cannot decode certain audio (DTS, AC3) or video (HEVC on older models). When playback fails the app
  offers to open the file in an external player such as VLC or MX Player.
- Without a TMDB key you get plain title cards instead of posters.
- Debug builds use a debug signing key, which is fine for installing on your own TV.
