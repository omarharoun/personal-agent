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
