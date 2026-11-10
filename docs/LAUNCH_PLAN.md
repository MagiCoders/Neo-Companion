# MochiLife: Launch Plan

Nov 10, 2026 · MochiLife team

Live version: https://claude.ai/artifact/LAfocss7wUB8EpdU9tReMB. Companion: [PROJECT_RECORD.md](PROJECT_RECORD.md).

## Goal and principles

The goal is an app that stays free to run for free users, earns from people who choose to pay, and does not break or run up a bill at 100,000 users or at 10 million.

Six rules carry the whole plan:

1. **Free users cost almost nothing.** Their features run on the phone: on-phone commands and on-phone AI. They need no account until they want cloud AI.
2. **Anything that costs money is checked on a server.** The phone is never trusted with limits, credits or purchases, because a modified app can change anything stored on the phone.
3. **No AI keys or direct AI calls in the released app.** The app talks to our own small server, and only the server talks to Gemini.
4. **Every cloud feature has a spending ceiling and an off switch** that work without publishing a new app version.
5. **Reliability before features.** A command either works or says clearly why not. Accuracy is measured with recorded voice clips, and a version ships only above the agreed pass rate.
6. **Google Play rules are design inputs,** not a final check: disclosed, opt-in accessibility help; no hidden recording; account deletion; honest data safety form.

No app can be made completely immune to cracking, and this plan does not claim that. It makes cracking pointless: what a cracked copy can unlock is already free, and what is worth money never lives on the phone.

## Three-layer design

Every request is answered by the cheapest layer that can do it well, and only the third layer costs money.

```
PHONE (asks, never decides)                 OUR SERVER (decides)
  Layer 1: on-phone commands  (free)          Four checks before spending:
  Layer 2: on-phone AI        (free)            1. valid Google sign-in
  Layer 3: cloud AI request  ---------------->  2. genuine app (Play Integrity)
           (sign-in token + app token,          3. energy left, taken in one step
            no AI key in the app)               4. rate limit and daily ceiling
                                                      |
  Google Play --(purchases, refunds)--> server        v
                                              Gemini (key only on the server)
```

Layers 1 and 2 never leave the phone. A Layer 3 request reaches Gemini only after the server's four checks, and Google Play tells the server, not the app, what was bought.

| Layer | Handles | Cost to us | Who gets it |
| --- | --- | --- | --- |
| 1. On-phone commands | Calls, WhatsApp, apps, YouTube, alarms, time, language switch | None | Everyone |
| 2. On-phone AI | Everyday chat, simple questions, understanding looser commands | None | Phones with Gemini Nano; Gemma 4 as an optional download of about 2.5 GB for phones with 8 GB of memory or more |
| 3. Cloud AI | Hard questions, any-language listening, natural voice, pictures | About $0.0015 to $0.0045 a turn | Paying users, and the welcome gift |

The app picks the layer itself: a matched command stays in Layer 1; otherwise Layer 2 if the phone has it and the request is simple; otherwise Layer 3 if the user has energy; otherwise she says what she can still do for free. The server piece is small: one Cloud Function for AI turns, one for purchases, one for ad rewards, with the user's energy in Firestore.

## Firebase: free or paid

Firebase cannot stay on the free plan for launch, but the paid plan (Blaze) keeps the same free amounts and bills only what goes above them, so free users can still cost close to nothing. The move to Blaze is needed for two things only: the server, and Gemini beyond the small free daily limit.

| Firebase part | Free plan (Spark) | On Blaze | Used for |
| --- | --- | --- | --- |
| Sign-in (Google) | 50,000 monthly users | Same free amount, then billed | Accounts for cloud AI and purchases |
| Firestore database | 1 GiB, 50,000 reads and 20,000 writes a day | Same free amount, then billed per use | Credits and purchases only |
| Cloud Functions (the server) | Not available | 2 million calls a month free, then $0.40 per million | Gateway, purchase checks |
| App Check | Free | Free, with daily limits per provider | Proves the app is genuine |
| Crashlytics | Free | Free | Crash and error reports |
| Cloud Messaging | Free | Free | Notifications |
| Remote Config | 100,000 fetches a day | Then $0.000006 per fetch | Remote switches and limits |
| Gemini through Firebase | Small daily limit for the whole project | Every request billed | Cloud AI |

Source: [Firebase pricing](https://firebase.google.com/pricing), read 10 November 2026.

How free users are kept free:

- **No account needed for on-phone features.** A free user who never uses cloud AI never touches sign-in, the database or the server.
- **Their data stays on the phone,** encrypted. The database holds one small record per signed-in user: credits, purchases, daily counters.
- **The app fetches remote settings at most once a day,** so even a million daily users stay cheap (about $5 a day at that size).
- **Cloud AI for free users is a small welcome gift plus optional rewarded ads,** not a daily allowance. See Packages.

Two things to check before passing 50,000 signed-in users: whether sign-in is billed above that number for this project (it is, if the project uses Identity Platform), and the App Check daily limit, which starts at 10,000 Play Integrity requests a day for the whole app and must be raised by request ([Play Integrity overview](https://developer.android.com/google/play/integrity/overview)).

Blaze has no built-in hard stop. The plan adds its own: a budget alert, a daily spending counter on the server, and a switch that pauses cloud AI for free users first when the counter is reached.

## Security: modded apps, cheating, hacking

The defence is one idea applied everywhere: the phone asks, the server decides. A cracked or modified copy can change anything on the phone, so nothing on the phone decides who gets paid features.

Every cloud request passes four checks on the server before any money is spent:

1. **Who is asking:** a valid Google sign-in token, verified by the server.
2. **Is this the real app:** an App Check token backed by Play Integrity. It passes only for our app, unmodified, installed from Google Play, on a genuine Android device.
3. **Are they allowed:** the server reads the user's credits and plan from its own database and subtracts the cost in one atomic step, so two requests cannot spend the same credit.
4. **Is it reasonable:** per-user and per-device rate limits, and a global daily spending ceiling.

Only then does the server call Gemini, with a key that exists only on the server.

| Attack | What the attacker tries | Defence |
| --- | --- | --- |
| Modded or cracked APK | Unlock paid features, remove limits | Paid features run on the server. A modified app fails the Play Integrity check and gets no token. |
| Repackaged copy on other stores | Ship our app with their ads or malware | Same check: not recognised by Google Play, so no cloud access. Report clones for takedown. |
| Stolen keys from the APK | Call Gemini on our bill | No AI key in the release app. The Firebase key is restricted to our package and signature, and App Check enforcement rejects everything else. |
| Fake purchase | Send a made-up or reused purchase receipt | The server verifies each purchase with Google Play directly, records the purchase token so it cannot be used twice, and listens for refunds and cancellations to take credits back. |
| Credit editing | Change the credit number | Database rules already block the app from writing credits or plan. Only the server can. |
| Farming free credits | Thousands of fake accounts or emulators | Welcome credits are given once per Google account and once per device, and only to devices that pass the integrity check. New-account spikes are rate limited. |
| Fake ad rewards | Claim rewards without watching | The ad network confirms each reward to our server directly; the app's own claim is ignored. |
| Clock tricks | Change the phone date to reset a daily limit | All time comes from the server. |
| Flooding | Run up our bill or knock the service over | Rate limits, the daily ceiling, automatic scaling with a maximum, and the remote switch. |
| Interception on public Wi-Fi | Read or alter traffic | HTTPS only, user-installed certificates ignored (already built). Tokens are short lived and tied to the app. |
| Phishing | Trick users into giving passwords | The app never asks for a password: Google sign-in only. It never opens links that arrive in messages. |
| Tricking the AI | A message that says "ignore your rules, send money" | Other people's text is passed as quoted data, actions are re-checked in code, and calls or messages need a real contact and a visible countdown (already built). |
| Stolen or lost phone | Read personal data | Personal data is encrypted with a key held in the phone's secure hardware; backups are off (already built). |
| Ransomware and data loss | Encrypt or destroy data | The app holds little on the server, the server database has daily backups and point-in-time recovery, and server access uses separate least-privilege accounts with two-step sign-in. |
| Reverse engineering | Study the code to find weaknesses | Code shrinking and name scrambling in release (already built). Nothing secret is in the app to find. |
| Insider or leaked server secret | Use our server keys | Keys live in Secret Manager, are rotated, and never appear in the repository. |

How strict to be: a rooted or unusual phone still gets all the free on-phone features. It is refused only cloud AI, with a clear message. This avoids punishing honest users with custom phones while protecting the bill.

What stays possible, honestly: someone can crack the app and use the on-phone features, which are free anyway. Someone with a paid account can share it; the server limits each account to a small number of devices. Neither costs us money.

## Packages, free tier, money

Free users get everything that runs on the phone without limit, plus a small one-time gift of cloud AI. Paying users buy energy, either as a pack or a monthly plan. One energy is one cloud AI turn; a reply in the natural voice costs 2 more.

**Free, forever, no account**

- Wake word, on-phone commands (calls, WhatsApp, apps, YouTube, alarms, time, language switch)
- On-phone AI chat where the phone supports it (Gemini Nano, or the optional Gemma 4 download)
- 3D Mochi, quests, floating overlay, the phone's built-in voice

**Free with a Google account**

- 20 welcome energy, once per account and device
- Optional: watch a rewarded ad for 2 energy, a few times a day

**Paid** (starting prices, to be tuned in the beta)

| Package | Price | Energy | AI cost if fully used | Share of what we receive |
| --- | --- | --- | --- | --- |
| Small pack | $0.99 | 200 | $0.30 | 36% |
| Medium pack | $2.99 | 700 | $1.05 | 41% |
| Large pack | $4.99 | 1,300 | $1.95 | 46% |
| Mega pack | $9.99 | 2,800 | $4.20 | 49% |
| Plus, monthly | $3.99 | 40 a day | $1.80 | 53% |
| Pro, monthly | $7.99 | 80 a day | $3.60 | 53% |

"What we receive" is the price minus Google Play's 15% fee. The cost column is the worst case, where every energy is spent; monthly plans are rarely used in full. Packs do not expire. Monthly plans also unlock the natural voice by default and priority when the service is busy.

Why a gift and not a daily free allowance: a daily allowance of even 5 turns costs up to $0.23 per active user per month, which is up to $22,500 a month for every 100,000 active free users with nothing coming back. A one-time gift lets people feel the difference once, costs $0.03, and the on-phone AI carries them after that.

Purchases go through Google Play Billing, as Google Play requires for digital goods. The server confirms each purchase with Google before adding energy.

## Risks at 10 million users

The two risks that can end the project are an uncontrolled bill and a Google Play removal; the rest are survivable with the defences below.

| Risk | What would happen | Defence |
| --- | --- | --- |
| Uncontrolled AI bill | A spike, a bug or abuse spends thousands in hours | Server-side credits, daily ceiling, free users paused first, budget alerts |
| Google Play rejection or removal | The app disappears from the store | Accessibility help stays rule based, opt-in and disclosed; no hidden recording; account deletion in the app; accurate data safety form; a policy review before each release |
| Gemini spending tier too low | Requests refused at launch because the account is new | Reach the higher tiers before launch: Tier 2 needs $100 spent and 3 days, Tier 3 needs $1,000 and 30 days |
| Play Integrity limit | Genuine users refused when the app passes 10,000 checks a day | Request the increase during the closed beta; keep tokens valid for hours, not minutes |
| Gemini outage or a preview model withdrawn | Cloud AI stops | The server can switch model or provider without an app update; the app falls back to on-phone AI and commands |
| Price change | Gemini 3.6 Flash doubles on 1 January 2027 | Most turns use Flash-Lite; energy prices are set on the server and can change the same day |
| Crashes on phones we never tested | Bad reviews, uninstalls | Crashlytics from the first beta, staged rollout (1%, 5%, 20%, 50%, 100%), halt on a crash-rate threshold |
| Viral growth faster than the server | Slow or failed requests | Automatic scaling with a set maximum; the gateway is stateless; a load test before public launch |
| WhatsApp or YouTube change their screens | Helper actions fail | Helpers fail safely and say so; the tap rules are delivered remotely so they can be fixed without an update |
| On-phone AI varies by phone | Slow or wrong answers on weak phones | Offer Gemma 4 only on phones with 8 GB of memory or more; measure speed on first run and fall back if slow |
| Refund abuse | Buy, spend, refund | The server hears about refunds from Google and removes the energy; repeat offenders lose purchasing |
| Privacy complaints | Voice sent to the cloud without clear consent | Plain consent screen before the first cloud turn; audio is not stored on our server; privacy policy lists Google as processor |
| Support load | Thousands of emails | In-app help, the diagnostics screen, and error messages that name the fix |

## Build order

Building starts with reliability, because a paid layer on top of an unreliable voice would only sell disappointment. Each phase ends at a gate that must pass before the next begins.

| Phase | Gate to pass |
| --- | --- |
| 1. Reliability version (start here) | 95% of recorded voice clips pass |
| 2. Server and packages | A modded test app gets no cloud AI |
| 3. On-phone AI | A free user needs no cloud for a day |
| 4. Closed beta | 99.5% crash-free, costs as planned |
| 5. Public launch | Each rollout stage holds for 3 days |

A phase that misses its gate is repeated, not skipped. Each phase ships as numbered versions (2.3, 2.4 and so on) with an APK and a changelog entry.

1. **Reliability version**
   - One state machine for the voice loop, with a timeout on every state and a watchdog that recovers a stuck microphone
   - The current state always visible on screen (listening, thinking, speaking, doing)
   - 15 core commands that work the same in English, Arabic and Persian, plus a language button
   - A set of recorded voice clips replayed automatically on every build, with the pass rate reported
   - Crashlytics and anonymous error reports; find and fix the alarm and timer failure
2. **Server and packages**
   - Cloud Functions gateway with the four checks; remove direct AI calls from the release app
   - Energy kept on the server; welcome gift; Play Billing packs and plans with server confirmation and refund handling
   - App Check with Play Integrity, enforced; remote switches and the daily ceiling
   - An attack test: a modified copy, a replayed purchase and a flood, all refused
3. **On-phone AI**
   - Gemini Nano where the phone has it; Gemma 4 as an optional download with a speed test on first run
   - The layer picker, and honest messages when a request needs energy
4. **Closed beta**
   - Google Play closed track, real purchases with test cards, a load test, a Google Play policy review
   - Measure real cost per turn and real paying share; retune prices
5. **Public launch**
   - Staged rollout with automatic halt on crash rate or cost alarm

After launch, in this order unless you say otherwise: file conversion by voice with a Mochi folder and sharing, phone setting controls, voice file search, voice training, streaming voice once the Live model is fast again.

## Cost tables

A cloud turn costs about $0.0015, and about $0.0045 with the natural voice. These are my estimates from the published prices; real costs must be measured in the beta.

| Item | Price | Per turn |
| --- | --- | --- |
| Gemini 3.5 Flash-Lite (quick and audio turns) | $0.30 per million tokens in, $2.50 out | About $0.0015 |
| Gemini 3.6 Flash (hard requests) | $0.75 in, $3.75 out; doubles 1 January 2027 | About $0.004 |
| Natural voice | $20 per million audio tokens out, 25 tokens a second | About $0.003 for a 6-second reply |
| Server | 2 million calls a month free, then $0.40 per million | Under $0.00001 |

What this means at different sizes, under stated assumptions: 40% of installs sign in, each gets 20 welcome energy, 2% of installs pay, and a payer brings $3 a month after Google's fee, of which 45% goes to AI.

| Installs | Welcome gifts, one time | Payers | Received per month | AI cost per month | Left per month |
| --- | --- | --- | --- | --- | --- |
| 100,000 | $1,200 | 2,000 | $6,000 | $2,700 | $3,300 |
| 1,000,000 | $12,000 | 20,000 | $60,000 | $27,000 | $33,000 |
| 10,000,000 | $120,000 | 200,000 | $600,000 | $270,000 | $330,000 |

The model's weak points: the 2% paying share is an assumption, not a measurement; Google's fee rises from 15% to 30% on earnings above $1 million a year; and server, database and sign-in costs are left out because they are small next to AI (a few percent), though sign-in may be billed above 50,000 monthly users.

If nobody pays, the cost is the welcome gifts only, and the gift size is a server setting that can be lowered to zero.

## Decisions needed from you

Phase 1 can start without any of these. Phase 2 cannot.

- [ ] **Report on version 2.2:** do AI listening and the natural voice work on your phone, and what does the voice log show for alarms and timers?
- [ ] **Free cloud AI:** a one-time gift of 20 energy (my recommendation), or a small daily allowance?
- [ ] **Rewarded ads:** yes or no? Yes adds income from free users but adds an ad library and its privacy disclosures.
- [ ] **Sign-in optional for free users:** yes (my recommendation) or required for everyone?
- [ ] **Natural voice:** paid only (my recommendation), or part of the free gift?
- [ ] **Package prices:** accept the starting table, or change it?
- [ ] **Blaze plan:** approve upgrading the Firebase project, and name a monthly budget for the beta (I suggest $50).
- [ ] **Google Play developer account:** personal or organisation? A new personal account must run a closed test with real testers before it may publish.
- [ ] **Beta testers:** 15 to 30 people with different phones, speaking English, Arabic and Persian.
- [ ] **Firebase connector:** start the build in a new chat so the connector you added is available.
