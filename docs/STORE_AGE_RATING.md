# Store age ratings — exact questionnaire answers (Google Play + Apple App Store)

This is the do-this-exactly guide for setting the **binding** store age ratings.
Personal Agent is an **adults-only (18+)** app. The in-app age gate (date-of-birth
confirmation on first launch) enforces access, but **the in-app gate does NOT set
the store rating** — the store rating is set by each store's questionnaire and
**must be consistent** with the in-app gate and the app's actual content.

> Answer every questionnaire **truthfully**. Both stores require the answers to
> match what the app actually does, and a mismatch (or an under-rating of the
> mental-health/crisis content) risks removal. Do **not** invent categories the
> app doesn't have (no gambling, no explicit sexual content, no real violence).

## What this app actually contains (the honest content profile)

Use this as the source of truth when answering both questionnaires:

- **Mental-health / crisis-support content.** A consent-first supportive surface,
  crisis-resource pointers (helplines/emergency-services guidance), and
  trusted-contact reach-out. References to distress, self-harm and suicide topics
  exist in supportive copy. **No graphic descriptions, no instructions, no
  encouragement** — it is supportive/sign-posting only. This is the single biggest
  driver of the rating.
- **AI-generated text.** An optional on-device language model can generate free-form
  text in response to the user. Output is **not filtered/curated by the developer**
  and is **user-facing UGC-like**. Treat as "AI-generated content / unmoderated
  user-generated content."
- **Unrestricted free-text + optional cloud calls.** The user can type anything;
  optionally (off by default) a minimized/anonymized request goes to a configured
  model provider over the network.
- **No** gambling/simulated gambling, **no** explicit sexual content or nudity,
  **no** realistic violence/gore, **no** profanity as a feature, **no** controlled-
  substance promotion, **no** in-app purchases or ads (today).

---

# Google Play — IARC content rating

**Where:** Play Console → your app → **App content** → **Content rating** → *Start
questionnaire*. (Also complete **App content → Target audience and content** —
see the bottom of this section.)

## Category & email
- **App category:** choose **"Reference, News, or Educational"** OR **"Utility,
  Productivity, Communication, or Other"** — pick the one that best matches the
  shipping app (it's a productivity/assistant app). Do **not** pick "Game."
- Provide a monitored **email address** (IARC sends the certificate there).

## Questionnaire answers (answer truthfully — these reflect this app)

| IARC question area | Answer | Why |
|---|---|---|
| Violence (realistic/cartoon/fantasy) | **No** | App has none |
| Sexuality / nudity / sexual content | **No** | App has none |
| Profanity / crude humor | **No** | Not a feature |
| Controlled substances (drugs/alcohol/tobacco reference or use) | **No** | None |
| Gambling / simulated gambling | **No** | None |
| **Scary / disturbing content; references to self-harm, suicide, or other sensitive mental-health topics** | **Yes** | The crisis-support surface references these topics (supportively) |
| **User-generated / user-to-user content, or unmoderated content (incl. AI-generated text)** | **Yes** | The on-device AI generates unmoderated free-form text; the user types free text |
| Does the app let users **interact / communicate** or **share content / location**? | **Interaction: see note**; **Share location: No** | No user-to-user social features; trusted-contact reach-out opens the dialer/SMS, it is not in-app social. If a question asks specifically about *user-to-user* communication, answer **No**; if it asks about generating/displaying *unmoderated content*, answer **Yes** (the AI). |
| Digital purchases / shares personal info with third parties | Answer per the build: **purchases No**; the optional cloud-escalation request is covered in Data Safety, not the content rating |

### Expected Play outcome
The combination that drives the rating up is **(a) the self-harm / suicide /
sensitive mental-health references** and **(b) unmoderated AI-generated content**.
Truthfully answering "Yes" to both typically yields a **"Mature 17+"** IARC rating
on Play, and can reach **"Adults only 18+"** depending on how the current
questionnaire weights the self-harm references. **Accept whatever the questionnaire
computes — do not hand-edit it down.** Then:

1. In **App content → Target audience and content**, set **Target age group to the
   18+ band only** (uncheck all younger bands).
2. **Declare the app is NOT directed to children.**
3. **Do NOT opt into Google Play Families / "Designed for Families."** (The
   self-harm/crisis content and unmoderated AI make the app ineligible anyway.)
4. Make sure the **store listing, Data Safety, and target audience are consistent**
   with an 18+ audience.

> ⚠️ If the questionnaire returns something lower than 17+, you have mis-answered a
> sensitive-content question — re-check the self-harm/suicide and unmoderated-AI
> items. The Play rating and the in-app 18+ gate **must not contradict each other**.

---

# Apple App Store — Age Rating questionnaire

**Where:** App Store Connect → your app → **App Information** (or the version's
**Age Rating** section) → **Edit** the Age Rating questionnaire.

Apple computes the band (**4+, 9+, 12+, 17+**, and the newer **18+** restriction)
from frequency answers: for each category you choose **None / Infrequent or Mild /
Frequent or Intense**.

## Questionnaire answers (truthful for this app)

| App Store category | Answer | Why |
|---|---|---|
| **Medical / Treatment Information** | **Infrequent/Mild** | The app surfaces crisis/mental-health support resources and supportive copy — health-adjacent information, but not a medical service or graphic treatment detail |
| **Mature / Suggestive Themes** | **Infrequent/Mild** | References to distress / self-harm / suicide appear in supportive context (drives the rating toward 17+) |
| **Horror / Fear Themes** | **None** | App has none |
| Cartoon or Fantasy Violence | **None** | None |
| Realistic Violence | **None** | None |
| Prolonged Graphic or Sadistic Realistic Violence | **None** | None |
| Sexual Content or Nudity / Graphic Sexual Content | **None** | None |
| Profanity or Crude Humor | **None** | Not a feature |
| Alcohol, Tobacco, or Drug Use or References | **None** | None |
| Simulated Gambling / Contests | **None** | None |
| **Unrestricted Web Access** | **No** | The app does NOT embed an open web browser. (If a future build adds an in-app browser/WebView to arbitrary URLs, set this **Yes** — it forces **17+** on its own.) |
| **Made for Kids** | **No / OFF** | The app is adults-only; never enable the Kids Category |
| **Age Assurance / age-restriction questions** (if the current questionnaire asks) | Declare the app is **age-restricted to adults** and that it **uses an in-app age gate** | Lets Apple apply the explicit **18+** restriction where offered |

### Expected Apple outcome
With **Medical/Treatment Information = Infrequent/Mild** and **Mature/Suggestive
Themes = Infrequent/Mild** (and everything else None), Apple's questionnaire lands
the app at **17+**. If the **current** App Store Connect questionnaire offers the
explicit **18+** age-restriction option (Apple has been rolling this out alongside
the new age bands), select it and declare the app **adults-only (18+)** so the App
Store rating matches the in-app gate. Keep **"Unrestricted Web Access" = No** and
**"Made for Kids" = Off**. As with Play, **answer truthfully** — Apple requires the
questionnaire to be consistent with the app's content and metadata, and will reject
or remove an under-rated app.

---

## Consistency checklist (both stores)

- [ ] In-app 18+ age gate present and enforced (already shipped — see
      `AgeGateScreen`/`AgeGateView`).
- [ ] **Google Play** IARC questionnaire answered truthfully → **17+ or 18+**;
      Target audience = **18+ only**; **not** Designed for Families.
- [ ] **Apple** age-rating questionnaire answered truthfully → **17+** (or **18+**
      restriction if offered); Web Access = No; Made for Kids = Off.
- [ ] Store listings, descriptions, screenshots, and Data Safety / privacy labels
      are all consistent with an **adults-only** app.
- [ ] Re-verify after any content change (e.g. adding an in-app browser, ads, IAP,
      or expanding the crisis content) — those can raise the required rating.

> Cross-references: `docs/PLAY_RELEASE.md`, `docs/APP_STORE_RELEASE.md`,
> `docs/PLAY_DATA_SAFETY.md`, `docs/PRIVACY_POLICY.md`, `docs/SECURITY_REVIEW.md`.
