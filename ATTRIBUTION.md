# Attribution

## Hermes Agent design system (visual design)

The Life Agent app's visual design — the **"Hermes Teal"** color palette
(`--background #041c1c` dark teal + `--midground #ffe6cb` warm cream, and the
resolved card/muted/border/accent blends), the small (0.5rem) corner radii, and
the signature **uppercase, wide-letter-spacing "display" labels** and monospace
metadata treatment — is **adapted from the Hermes Agent web dashboard and the
`@nous-research/ui` design system**, both of which are MIT-licensed.

We re-implemented these design tokens natively in Jetpack Compose
(`androidApp/.../ui/theme/Color.kt`, `Theme.kt`). No source code, CSS, fonts, or
image assets from the Hermes project are copied or bundled into this app — only
the visual design language (colors, spacing, radii, typographic treatment) was
reproduced. The boutique brand fonts (Collapse, Rules, Mondwest) and the Hermes
name/logo are **not** used; the app uses system fonts and its own "Life Agent"
name.

Per the MIT License, the original copyright and permission notice are preserved
below.

```
MIT License

Copyright (c) 2025 Nous Research

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Sources:
- Hermes Agent — https://github.com/NousResearch/hermes-agent (repo LICENSE: MIT)
- `@nous-research/ui` v0.18.2 (package `license: "MIT"`)

## Vosk — on-device speech-to-text engine (Apache-2.0)

Offline voice input is powered by **Vosk** (`com.alphacephei:vosk-android:0.3.47`),
an open-source speech recognition toolkit. The engine's native library
(`libvosk.so`) is bundled in the APK, and the small English acoustic/language model
(**`vosk-model-small-en-us-0.15`**, ~40 MB) is downloaded once on first voice use
into app-private storage. All audio capture and transcription happen on the device;
no audio leaves the phone and no cloud speech service is used. Vosk (and its JNA
dependency, `net.java.dev.jna:jna`, also Apache-2.0) is used under the Apache License
2.0. The Vosk models are distributed under Apache-2.0 as well.

We evaluated **whisper.cpp / whisper.tflite** (MIT) as an alternative on-device
engine; it is a fine choice but requires compiling native code and hand-rolling the
audio loop, whereas the Vosk AAR ships a ready-to-use streaming recognizer over
`AudioRecord`. We chose Vosk for the lower integration risk. (Whisper is MIT-licensed;
credited here in case it is adopted later.)

Sources:
- Vosk API — https://github.com/alphacep/vosk-api (LICENSE: Apache-2.0)
- Vosk models — https://alphacephei.com/vosk/models (Apache-2.0)
- JNA — https://github.com/java-native-access/jna (Apache-2.0)
- whisper.cpp — https://github.com/ggerganov/whisper.cpp (LICENSE: MIT) — evaluated alternative

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   Copyright 2020-2024 Alpha Cephei Inc. (Vosk) and the JNA authors.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
