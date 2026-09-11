# Hometown 0.2.1 rendering sharpness

The cause was a second background-blur pass. Minecraft 1.21.1's `Screen.render()` calls `renderBackground()` before drawing widgets. The previous Ledger explicitly drew its background, parchment, and page text before calling `super.render()`. That inherited call blurred the completed pages, then drew crisp bookmarks. This matches the supplied screenshot. The render order was checked against the mapped Minecraft class and NeoForge's Screen patch.

The Ledger now uses the inherited render pass once. Its background override first draws the vanilla background, then the parchment and page contents. The inherited render method subsequently draws the existing bookmarks; hover tooltips render last.

The native layout is 384 by 224 GUI pixels, centered with integer `(width - bookWidth) / 2` and `(height - bookHeight) / 2`. Smaller viewports retain the existing reduced layout bounds, without scaling artwork or fonts. Minecraft controls global GUI scale. Book colors, page contents, and bookmark drawing code remain unchanged. Only TownLedgerScreen changed among runtime Java sources.

## Rendering audit

- No Ledger PoseStack scaling or push/pop calls, hence no unbalanced pose stack.
- No custom fractional GUI scale or scaled font renderer.
- All book and page positions are integer GUI coordinates.
- No Ledger intermediate texture/framebuffer or texture filtering overrides. Parchment is drawn with native GUI fills.
- Vanilla background blur is retained, but executes before all Ledger foreground drawing.

## Verification

All 37 automated tests pass. Screen tests exercise the actual inherited render dispatch, stubbing only GPU background operations. They require exactly one blur before all artwork/text, no pose access, centered integer bounds, contained page text, and working content on all four tabs.

Viewport coverage: 960x540, 640x360, and 480x270 represent GUI scales 2, 3, and 4 at 1920x1080. Also tested 959x488, 640x326, and 480x244 for the supplied screenshot's window size, plus 320x240. These are recording-backend tests, not GPU screenshots; actual font/parchment sharpness and input behavior at each scale still require an in-game check.

Java 21 compilation succeeded. Standard offline Gradle was retried but failed in Minecraft artifact extraction with the existing Windows archive-close AccessDeniedException before mod compilation. The delivered JAR uses the same direct JavaCompiler compilation and packaging route as the previously working mod. See VALIDATION.md and TEST-RESULTS.txt.
