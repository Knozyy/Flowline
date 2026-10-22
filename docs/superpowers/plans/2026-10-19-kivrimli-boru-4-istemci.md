# Kendi kıvrımlı boru motoru — Plan 4/6: İstemci (E)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Oyuncunun kıvrımlı boruyu görmesi ve döşemesi: kare cam kanal + köşe rayları mesh'i, yumuşak cam düğüm, uç yakası/çıkış
halkası, önizleme, HUD, onaylı tuş düzeni, altı grid modu, seçenek çarkı, sol tıkla kırma, kıvrımlı uç ekranının cilası ve
boyama. Sol el mod tuşu artık Kıvrımlı'ya geçer.

**Architecture:** Saf hesap (`com.knozyy.flowline.curve`) yine GameTest'le sınanır: grid adayları ve ekran seçimi
(`GridCandidates`), tüp mesh'i (`TubeMesh`, dörtgen listesi). İstemci kodu (`com.knozyy.flowline.client.curve`)
`ClientCurves` verisini çizer (`RenderLevelStageEvent`, `AFTER_TRANSLUCENT_BLOCKS`; raylar `entitySolid`, cam
`entityTranslucent`, blok atlası), hedefi bulur ve Forge'un `InputEvent.InteractionKeyMappingTriggered` olayıyla sağ/sol/
orta tıkı Mixin'siz yakalar; istekleri Plan 3'ün paketleriyle gönderir. Önizleme istemcide `CurveSolver` +
`PlacementValidator` ile çözülür, son söz sunucunundur.

**Tech Stack:** Java 17, Forge 47.3.0 (MC 1.20.1), GameTest (saf kısımlar).

**Spec:** `docs/superpowers/specs/2026-10-18-kivrimli-boru-kendi-motor-design.md` ("Görünüm", "El düzeni", tuş tablosu,
"Aynı item").
**Davranış kaynağı:** ek B6 (grid, düzenleme) ve grid tabloları.
**Arayüzler:** Plan 2 ve Plan 3 planlarının Interfaces blokları.

## Global Constraints

- Hedef Minecraft 1.20.1 / Forge 47.3.0 / Java 17 (kayıt desenleri ve tür `switch`'i yok). Dal `feature/own-curved-pipes`.
- `com.knozyy.flowline.curve` Minecraft'tan bağımsız kalır (saflık testi). İstemci kodu `client`/`client.curve`
  paketlerinde; sunucuda yüklenen sınıflara istemci türü girmez.
- Testler GameTest; başlangıç **168**. Doğrulama: `.\gradlew.bat runGameTestServer --console=plain` →
  `run\gametest-normal\logs\latest.log` içinde `All <N> required tests passed`. Görsel ve girdi davranışı GameTest'le
  sınanamaz: **istemci ancak kullanıcı izin verince açılır**; açılmazsa "gözle doğrulanmadı" diye raporlanır.
- Görünüm blok borusuyla aynı ölçüler (`tools/gen/models.py`): kanal 6×6 px (yarı genişlik `3/16`), raylar köşelerde
  1×1 px (`[5,6]` ve `[10,11]`), cam `0.5 px` içeride (`[5.5,10.5]`), yaka 8×8×2 px, çıkış halkası 10×10×2.5 px.
  Raylar boyanın rengi, boyasızsa `Theme.accent(type)`; cam türün `*_pipe_glass` dokusu.
- Kaynaklar `tools/gen_resources.py` ile; ilgisiz `structures/*.nbt` değişimleri geri alınır. Her derlemede sürüm artar.
- Ağ protokolü Plan 3'te 12'ydi; yeni paket (boyama) eklendiği için **13** olur.
- Commit kimliği Knozyy, atıf yok, push yok.

## Review Focus

1. **Kıvrımlı modda boru elde, bakılan şey blok:** sağ tık blok koymamalı, hattı başlatmalı/sürdürmeli; Build for Me
   modunda eski davranış aynen. Test: Görev 6 `curvedModeClaimsTheUseKey` (saf karar fonksiyonu).
2. **Çok sayıda kenar:** her karede mesh yeniden kurulmamalı (kenar başına önbellek, kenar değişince düşer). Test: Görev 3
   `meshCacheRebuildsOnlyChangedEdges`.
3. **Grid adayının blok içinde kalması:** genişletilmiş outline içindeki aday elenmeli. Test: Görev 1
   `gridDropsCandidatesInsideBlocks`.
4. **Kamera arkasındaki aday:** seçilmemeli. Test: Görev 1 `gridPicksNearestToTheCrosshair`.
5. **Sunucunun reddi:** HUD yerel önizlemeye değil sunucunun sonucuna göre hata göstermeli. Test: Görev 7
   `resultTextFollowsTheServer` (saf metin seçimi).

---

### Görev 1: Grid adayları ve ekran seçimi (saf)

**Files:** Create `curve/GridMode.java`, `curve/GridCandidates.java`; Test `test/CurveGridGameTests.java`

**Interfaces — Produces:**
- `enum GridMode { OFF, AXIAL, CORNER_P, CORNER_N, EDGE_P, EDGE_N }`: `GridMode next()` (`0→1→…→5→0`),
  `GridMode previous()` (`0→5→…→1→0`), `String key()` = `"hud.flowline.grid." + name().toLowerCase()`.
- `static List<Vec3d> GridCandidates.of(GridMode mode, int x, int y, int z)` — hücre merkezi `C=(x+.5,y+.5,z+.5)`'e
  göre (ek B6): OFF boş; AXIAL `C` + her eksende `±0.25, ±0.5` (13); CORNER_P `(±0.25,±0.25,±0.5)` ve büyük eksenin üç
  seçimi (24); CORNER_N `(±0.25,±0.25,±0.25)` (8); EDGE_P `(0,±0.25,±0.5)` eksen permütasyonları (24); EDGE_N
  `(0,±0.25,±0.25)` permütasyonları (12). Tekrarsız.
- `static List<Vec3d> GridCandidates.free(List<Vec3d> candidates, List<Aabb> outlines)` — her outline `0.126`
  genişletilir; içinde kalan aday atılır.
- `static Optional<Vec3d> GridCandidates.pick(List<Vec3d> candidates, Vec3d eye, Vec3d look)` — kameranın önündeki
  (`(p−eye)·look > 0`) adaylar arasında `x²+y²`'si en küçük olan; `x,y` bakış eksenine dik iki birim eksene
  `(p−eye)/((p−eye)·look)` izdüşümü (ekran projeksiyonu).

- [ ] Step 1: Failing testler — `gridCountsMatchTheTables` (13/24/8/24/12, OFF 0, tekrarsız), `gridModesCycleInOrder`
  (next/previous dizileri), `gridDropsCandidatesInsideBlocks` (hücrenin altındaki tam blok: y−0.5 adayı atılır, y+0.5
  kalır), `gridPicksNearestToTheCrosshair` (iki aday: biri bakışın 0.1 yanında, biri tam önünde ama arkada → öndeki
  yakın olan seçilir; hepsi arkadaysa boş).
- [ ] Step 2: Derleme hatası. Step 3: yaz. Step 4: `All 172 required tests passed`. Step 5: Commit `Add the grid modes`.

### Görev 2: Tüp mesh'i (saf)

**Files:** Create `curve/TubeMesh.java`, `curve/MeshQuad.java`; Test `test/CurveMeshGameTests.java`

**Interfaces — Produces:**
- `enum MeshPart { RAIL, GLASS, GLASS_INNER, COLLAR, RING, NODE_GLASS }`
- `record MeshQuad(MeshPart part, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal, float u0, float v0, float u1, float v1)`
  — köşeler saat yönünün tersine (normal dışa), UV piksel (0–16).
- `static List<MeshQuad> TubeMesh.edge(List<Vec3d> points, EndStyle start, EndStyle end)` —
  `enum EndStyle { NONE, COLLAR, RING, CAP }`; nokta başına çerçeve: teğet = komşu segment yönlerinin ortalaması,
  normal = `Frames.normals`'ın komşu segmentlerdeki değerlerinin ortalaması (teğete dik, birim). Her segment: 4 ray × 4
  yüz, 4 cam dış + 4 cam iç yüz; her 4. noktada bilezik (1 px genişlik, yarı genişlik `3.5/16`, 4 yüz × 4 kenar).
  `COLLAR`/`RING` uçta blok borusundaki ölçülerle; `CAP` serbest uçta cam kapak.
- `static List<MeshQuad> TubeMesh.joint(Vec3d centre, double radius, List<Vec3d> exits)` — yumuşak cam düğüm:
  8×6 dilimli UV küre (`NODE_GLASS`), her çıkış yönünde ince bilezik (`RING`).

- [ ] Step 1: Failing testler — `straightTubeHasTheBlockPipeProfile` (düz 4 segment: ray quad'larının hepsi `[2/16,3/16]`
  yarı-genişlik bandında; cam dış yüzü `2.5/16`'da; quad sayısı = segment·(16+8) + bilezikler), `tubeQuadsFaceOutwards`
  (dirsekte her dış quad'ın normali merkezden uzağa), `endStylesAddTheirPieces` (COLLAR ve RING ek quad üretir, NONE
  üretmez), `jointIsAClosedSphere` (küre quad'larının köşeleri merkezden `radius` uzakta, 1e-9).
- [ ] Step 2–4: `All 176 required tests passed`. Step 5: Commit `Mesh curved pipes as glass ducts`.

### Görev 3: Çizim

**Files:** Create `client/curve/CurveRenderer.java`, `client/curve/MeshCache.java`; Modify `client/ClientCurves.java`
(sürüm sayacı, değişen kenar bildirimi); Test `test/CurveMeshGameTests.java` (`MeshCache` saf kısmı)

**Interfaces — Produces:**
- `final class MeshCache` (saf, `curve` ve `ClientCurves` verisiyle): `List<MeshQuad> edge(long id)`, `void invalidate(long id)`,
  `int builds()` (test sayacı). Uç stili: düğüm anchor türü `2` (blok yüzü) → `extract ? RING : COLLAR`; `1` (boru yüzü) →
  `NONE`; serbest ve tek kenarlı → `CAP`; ≥3 kenar ya da force → `NONE` (düğüm küresi çizer).
- `CurveRenderer` (`@Mod.EventBusSubscriber(value=CLIENT)`): `RenderLevelStageEvent` `AFTER_TRANSLUCENT_BLOCKS`; kamera
  göreli; görüş konisi dışında kalan kenarı atlar (`Frustum.isVisible(bounds)`); ışık `LevelRenderer.getLightColor`
  quad merkezinin hücresi; RAIL/COLLAR/RING → `RenderType.entitySolid(BLOCK_ATLAS)` ray dokusu + renk; GLASS/NODE_GLASS →
  `entityTranslucent(BLOCK_ATLAS)` türün cam dokusu.

- [ ] Step 1: Failing test `meshCacheRebuildsOnlyChangedEdges` (iki kenar; iki kez iste → 2 kurulum; birini invalidate,
  ikisini iste → 3).
- [ ] Step 2–4: `All 177 required tests passed` + `.\gradlew.bat compileJava` temiz. Step 5: Commit `Draw curved pipes`.

### Görev 4: Hedef bulma ve vurgulama

**Files:** Create `client/curve/CurveTarget.java`, `client/curve/CurveHighlight.java`; Test `test/CurveGridGameTests.java`
(saf karar)

**Interfaces — Produces:**
- `sealed`-benzeri `final class CurveTarget` (kind: `NODE, EDGE, PIPE_FACE, BLOCK_FACE, AIR`, nodeId, edgeId, segment,
  along, pos (Vec3d), blockPos, face): `static CurveTarget find(ClientLevel, ClientCurves, Vec3d eye, Vec3d look,
  double reach, GridMode grid, boolean snap)`. Öncelik (snap açıkken): bakış ışınına `R`'den yakın düğüm → ışının
  çarptığı kıvrımlı kenar (blok vuruşundan yakınsa) → blok borusu yüzü → blok yüzü (outline, vuruş noktası) → havada
  grid adayı (vurulan bloğun önündeki hücre ya da reach ucundaki hücre); snap kapalı (Ctrl) → yalnız AIR (grid, ya da
  grid OFF ise reach'in ucundaki nokta).
- Saf karar: `static Kind CurveTarget.choose(double nodeDist, double edgeDist, double blockDist, boolean snap)`.
- `CurveHighlight`: hedef düğüm/kenarsa ince çizgi kutu (`RenderType.lines()`), `RenderHighlightEvent.Block`'u bastırır
  (kenar bloktan yakınsa).

- [ ] Step 1: Failing test `targetPrefersNodesThenEdges` (choose: düğüm < kenar < blok sıraları; snap false → AIR).
- [ ] Step 2–4: `All 178 required tests passed`. Step 5: Commit `Find what the player points at`.

### Görev 5: Boyama (sunucu + paket)

**Files:** Modify `curved/CurveEdits.java` (`paint`), Create `network/curve/CurvePaintPayload.java`, Modify
`network/CurveMessages.java`, `network/ModNetwork.java` (`PROTOCOL = "13"`); Test `test/CurveEditGameTests.java`

**Interfaces — Produces:** `static EditResult CurveEdits.paint(ServerPlayer p, long edgeId, InteractionHand hand)` —
eldeki `DyeItem`; kenarın rengi aynıysa siler (`NO_COLOR`), değilse boyar; survival'da boya azalır; reach; uçtaki
blok borusu yüzleri yeniden hesaplanır (renk uyuşmazsa bağ kopar).
`record CurvePaintPayload(int requestId, long edge, InteractionHand hand)`.

- [ ] Step 1: Failing testler `paintingAnEdgeSplitsTheNetwork` (boru beyaz, kenar kırmızı → yüz `Conn.NONE`; aynı boya
  ikinci kez → renk silinir, yüz `Conn.PIPE`; survival'da 1 boya eksilir), `curvePaintPayloadRoundTrips`.
- [ ] Step 2–4: `All 180 required tests passed`. Step 5: Commit `Dye curved pipes`.

### Görev 6: Döşeme denetleyicisi ve tuşlar

**Files:** Create `client/curve/CurvePlacer.java` (durum makinesi), `client/curve/CurveInput.java` (Forge olayları);
Modify `client/Keys.java` (`OPTIONS` sol Alt, `NO_SNAP` sol Ctrl; Controls'tan değiştirilebilir), `client/OffhandPipe.java`
(`curvedAvailable()` true; CURVED'de HUD/önizleme `CurvePlacer`'a), `client/ClientSetup.java` (tuş kaydı); Test
`test/CurveGridGameTests.java` (saf karar)

**Davranış (spec tuş tablosu):**
- Sol elde boru + mod CURVED: **sağ tık** hedefe göre: boşta → hattı başlat (ilk uç); çiziliyor ve hedef AIR → iç nokta
  ekle; hedef NODE/PIPE_FACE/BLOCK_FACE → bitir (`CurvePlacePayload`); **Shift+sağ tık** havada bitir.
  **Sol tık** bakılan kenarı kır (`CurveBreakPayload`). **Orta tık / Shift+orta** grid ileri/geri. **Ctrl basılı**
  yapışma kapalı. **Esc ya da B** yarım hattı iptal. Bu tuşlar `InteractionKeyMappingTriggered`'ı iptal ederek
  vanilya eylemini (blok koyma/kırma/seçme) durdurur.
- Mod CURVED değilken de: bakılan kenara/düğüme **sol tık** kırar (sol elde boru şartı yok); **boya ile sağ tık** kenarı
  boyar; **kıvrımlı uca sağ tık** ekranını açar (`CurveEndPayload.OPEN`), **anahtarla Shift+sağ tık** modunu değiştirir
  (`CYCLE`), **Filter Card ile** kartı uygular (`CurveEndPayload` yerine sunucu `useCardOnEnd`: yeni eylem `CARD`).
- Saf karar fonksiyonu: `static Action CurvePlacer.decide(boolean curvedMode, boolean drawing, CurveTarget.Kind target,
  boolean shift, Click click, HeldKind held)` → `START, ADD, FINISH, BREAK, PAINT, OPEN_END, CYCLE_END, CARD_END, GRID_NEXT,
  GRID_PREVIOUS, PASS`.

- [ ] Step 1: Failing testler `curvedModeClaimsTheUseKey` (CURVED + blok hedef + sağ tık → START; Build for Me → PASS),
  `drawingAddsThenFinishes` (AIR → ADD, BLOCK_FACE → FINISH, Shift+AIR → FINISH), `leftClickBreaksPointedEdges`
  (EDGE + sol tık her modda BREAK; blok hedef → PASS), `endsOpenLikeSides` (BLOCK_FACE düğümü + sağ tık boş el → OPEN_END,
  anahtar+Shift → CYCLE_END, kart → CARD_END).
- [ ] Step 2: `CurveEndPayload.Action`'a `CARD` eklenir (sunucuda `useCardOnEnd`, ana el).
- [ ] Step 3–4: `All 184 required tests passed`. Step 5: Commit `Lay curved pipes with the mouse`.

### Görev 7: Önizleme, HUD, metinler, seçenek çarkı

**Files:** Create `client/curve/CurvePreview.java`, `client/curve/CurveHud.java`, `client/curve/OptionWheel.java`;
Modify `tools/gen/lang.py` (+ üretilen lang), `client/PipeScreen.java` (kıvrımlı uçta sekmeler gizli, başlık
`gui.flowline.curved_end`); Test `test/CurveGridGameTests.java` (saf metin)

**Davranış:**
- Önizleme: çizilen zincir + hedef noktası istemcide çözülür; geçerliyse türün renginde yarı saydam tüp (`TubeMesh`),
  `PlacementValidator` (istemci `BlockShapes`) hata verirse kırmızı; çözüm yoksa kırmızı düz çizgi.
- HUD (artı işaretinin altında): mod adı, ipucu (`hud.flowline.curve.start/add/finish/finish_air`), grid adı, maliyet
  `hud.flowline.curve.cost` (eldeki boru azsa kırmızı), son sunucu sonucu 3 saniye (`PlaceError.key()`, `denied`,
  `too_costly` eksik sayısıyla). Yerel önizleme hatası yalnız "önizleme" rengiyle, sunucu sonucu metinle.
- Seçenek çarkı: sol Alt basılıyken kaydırma 6 seçenek arasında döner (Sil, Dallandır, Uzat, Taşı, Force Joint,
  Ayarlar), bırakınca bakılan düğüm/kenarda uygulanır (Sil → `CurveRemovePayload`/`CurveBreakPayload`; Dallandır/Uzat →
  o düğümden çizime başla; Taşı → düğümü taşı modu, sağ tık yeni yere `CurveTranslatePayload`; Force Joint →
  `CurveForceJointPayload(!force)`; Ayarlar → `CurveEndPayload.OPEN`).
- Saf: `static String CurveHud.resultKey(CurveResultPayload r)` → `null` (başarı) ya da çeviri anahtarı.

- [ ] Step 1: Failing test `resultTextFollowsTheServer` (code 0 → null; 4 → `message.flowline.curve.too_far`; denied →
  `message.flowline.curve.denied`; 3 → `too_costly`).
- [ ] Step 2: lang: HUD, grid adları, çark seçenekleri, `gui.flowline.curved_end`, `key.flowline.options`,
  `key.flowline.no_snap`; `tooltip.flowline.curved.hands` → "Main hand: block pipe | Off hand: Build for me or curved
  ([%s] switches)" / TR karşılığı; `hud.flowline.build.ready` eski "[%s] switches to Curved" kısmı geri gelir.
- [ ] Step 3–4: `All 185 required tests passed`; `.\gradlew.bat build` → exit 0 (sürüm `0.3.12+1.20.1`);
  `-PwithMekanism` aynı sayı.
- [ ] Step 5: Commit `Preview curved pipes and show what happens`.
- [ ] Step 6 (kullanıcı): istemci açılışı ve gözle kontrol (görünüm, geçiş, önizleme, tuşlar). İzin yoksa "doğrulanmadı".
