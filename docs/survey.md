# Survey Feature Documentation

The EmuLinker-K survey feature enables server administrators to periodically poll players about their connection quality and securely bundle this data with telemetry (GameLog proto) for analysis.

## Workflow

1. **Game Eligibility**
   Before the server prompts any players or tracks timing, it dynamically evaluates the game being played. The game's rom name must match at least one of the regex patterns specified in the administrator's `survey.gameWhitelist` configuration options. If none match (or if the whitelist is empty), the survey subsystem remains entirely dormant for that specific game.

2. **Consent Request**
   When players join or create an eligible game, they are prompted via a server message asking if they'd like to participate in an academic survey mapping lag experiences. They must answer `"yes"` in chat *within exactly 4 minutes* of the prompt to successfully enroll. Non-responses or answers of `"no"` will permanently exclude them from the survey loop for the session.

3. **Wait Period**
   Starting from the point the game status shifts to `PLAYING` (when all players synchronize and inputs begin processing), an 8-minute cooldown is initialized. No survey prompts will trigger during this time.

4. **Intelligent Triggering**
   Rather than interrupting gameplay randomly for everyone after 8 minutes, the server waits for a moment of downtime to issue the prompt. 
   - After the 8-minute window finishes, anytime *any* player presses the **START** button on their controller (typically signaling a pause or break in the action), a check is performed. To minimize latency overhead, this bitmask verification against the raw game state (`ByteBuf`) only executes once every 3 frames per player.
   - If a **START** button press is detected, the survey is immediately dispatched to *all* currently eligible players in the game (those who have consented and have not been surveyed in the last 10 minutes).

5. **Telemetry Snapshot (5-Minute Rolling Window)**
   To optimize memory use and ensure matching data records, the `GameLog` accumulator automatically culls telemetry entries exceeding 5 minutes old in real time. 
   - Exactly when the survey prompt triggers, a snapshot of this 5-minute protocol log is created and cached locally in the game scope.
   - This prevents memory overflow during long play sessions and makes certain that the telemetry represents the exact window of gameplay *before* the prompt appeared.

6. **Response and Transmission**
   Players have precisely 1.5 minutes to cast a response (ratings from `1` to `3`). If valid feedback is given within this timeframe:
   - The system intercepts the answer in the game chat.
   - It collates their rating alongside the snapshotted 5-minute proto log array.
   - This data payload can then be serialized over the wire to an analytical backend server (`reportSurveyResponse` stub).

## Technical Implementation

- **`RuntimeFlags`**: Manages the `surveyEnabled` global toggle and `surveyGameWhitelist` array via configuration.
- **`KailleraUser`**: Houses properties to handle boolean `surveyConsent` and internal `TimeMark` cooldown mechanisms explicitly handling consent wait times (`surveyConsentAskedTimeMark`) and answer timeouts (`lastSurveyAskedTimeMark`).
- **`KailleraGame`**: Drives the logical matrix. Tracks individual and global timers, polls raw protocol data for matching start-button bitmasks `(data.getByte(index + 12) & 0x10)`, generates protocol log snapshots, and interfaces directly with the users' chat streams.
- **I18n**: All prompting and system feedback text resolves through the established localization framework (`messages.properties` > `EmuLang`).
