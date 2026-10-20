# Kendi kıvrımlı boru motoru — Plan 3/6: Dünya verisi, ağ ve sunucu düzenleyicisi (D)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kıvrımlı boruları dünyaya bağlamak: dünya başına kayıt, `PipeNetwork`'ün kıvrımlı kenar ve uçlar üzerinden
yürümesi, kıvrımlı uçta blok yüzüyle aynı `SideConfig`/yükseltme/ekran, sunucuda yerleştir/kır/düzenle (maliyet, iade,
koruma, reach), dünya değişimlerinde kırılma ve istemciye chunk senkronu. Bitince GameTest'te tek bir Extract, kıvrımlı
hattan geçerek sandıktan sandığa taşır.

**Architecture:** Plan 2'nin saf çekirdeği (`com.knozyy.flowline.curve`) olduğu gibi kullanılır; Minecraft'a dokunan her
şey yeni `com.knozyy.flowline.curved` paketindedir. Ağ, "port" kavramıyla genelleşir: bir blok borusunun yüzü ve bir
kıvrımlı uç aynı `Port` arayüzünü uygular; aktarım kodu (`ItemTransfer` vb.) değişmez. Ağ taraması Dijkstra olur; blok
adımı 1, kıvrımlı kenar `max(1, cost)` sayılır. İstemci tarafında yalnız veri deposu ve paket işleyicileri vardır; çizim,
önizleme ve tıklama Plan 4'tedir.

**Tech Stack:** Java 17, Forge 47.3.0 (MC 1.20.1), ForgeGradle 6, Forge GameTest.

**Spec:** `docs/superpowers/specs/2026-10-18-kivrimli-boru-kendi-motor-design.md` ("Ağ bütünleşmesi", "Dünya değişimleri ve
bağlantı noktaları", "Kısıtlar").
**Davranış kaynağı:** `C:\Users\kanad\Desktop\Projeler\mod editörü\CurvyAdditionForFlowLine\docs\curvy-behaviour-addendum-re.md`
("ek"; B1 hata sırası, B4 dünya değişimi, B5 uçlar, B6 işlemler).
**Çekirdek arayüzleri:** `docs/superpowers/plans/2026-10-18-kivrimli-boru-2-geometri-cekirdegi.md` (Interfaces blokları).

## Global Constraints

- Hedef: Minecraft 1.20.1, Forge 47.3.0, Java 17; dal `feature/own-curved-pipes`.
- `com.knozyy.flowline.curve` değişmez (saflık testi sürer); Minecraft kodu `com.knozyy.flowline.curved` paketine girer.
- Curvy kodu kopyalanmaz; yalnız ekteki davranış kullanılır.
- Testler GameTest'tir (JUnit bu yolda çalışmıyor). Başlangıç sayısı **123**. Doğrulama:
  `.\gradlew.bat runGameTestServer --console=plain` → `run\gametest-normal\logs\latest.log` içinde
  `All <N> required tests passed`; Türkçe yerelde "BUİLD" yazılır. PowerShell'de dosya yolları mutlak verilir (.NET
  göreli yolu farklı dizine çözer).
- Kıvrımlı testler yeni `curve` GameTest yapısını kullanır (9×6×9, `tools/gen/gametest.py` üretir; Görev 4).
  Her test kendi eklediği kıvrımları `CurveTestSupport.clear(helper)` ile siler, çünkü `CurveData` test seviyesinde
  testler arasında paylaşılır.
- Kaynak dosyalar (lang, yapı) `tools/gen_resources.py` ile üretilir; betiğin dokunduğu ilgisiz dosyalar
  (`structures/empty.nbt` gzip zaman damgası) `git checkout --` ile geri alınır.
- Ağ protokolü bu planda bir kez `"12"`'ye çıkar (Görev 10).
- **Derleme, GameTest ve oyun başlatma kullanıcıya sorulmadan çalıştırılmaz.** Her derlemede `mod_version` artar.
- Commit kimliği `Knozyy <167627012+Knozyy@users.noreply.github.com>`, **atıf satırı yok**. Push yok.

### Bu planın kararları (spec "builder'a bırakılanlar" ve ek)

| Konu | Karar | Gerekçe |
| --- | --- | --- |
| Kayıt | `SavedData` `"flowline_curved_pipes"`, NBT; noktalar `long[]` (`Double.doubleToRawLongBits`), yüklemede yeniden çözülmez | Eski `flowline_curves.dat` ve `LegacyCurveNotice` olduğu gibi kalır; birebir aynı şekil |
| Mesafe | Blok adımı 1; kıvrımlı kenar `max(1, cost)` (yuvarlanmış uzunluk) | Uzun kıvrım uzak sayılır; "nearest/farthest" doğal kalır |
| Bağlı blok kırılınca | Uç **bağlı kalır**, `SideConfig` ve yükseltmeler korunur; blok geri gelince yeniden çalışır | Ek B5 (Curvy, yaklaşık) ve Flowline blok borularının davranışı. **Spec'in "serbest kalır, ayarlar silinir" önerisinden sapma; kullanıcıya bildirilir.** |
| Uç taşınıp bloktan ayrılınca | Uç serbest olur, `SideConfig` silinir, yükseltmeler uç konumunda eşya olarak düşer | Spec ve ek B5 (`detach` uyarısı) |
| Blok borusu yüzündeki düğüm | Düğüm yüz merkezine oturtulur (`pos + 0.5 + 0.5·n`), yön yüz normali; o yüz `Conn.PIPE` olur | Blok kanalıyla kıvrımlı kanal aynı noktada birleşir |
| Kıvrımlı uçta kızıltaş | Denetim: uç düğümünün hücresinde `hasNeighborSignal`. Çıkış (`SignalMode`) kıvrımlı uçta yok sayılır | O hücrede duran bir blok borusu gibi davranır; ucun sinyal verecek bloğu yok |
| Renk | Kenar başına renk; kenar–kenar ve kenar–blok borusu bağı `colorsMatch` ile. Boyama işlemi Plan 4 | Blok borusu kuralı |
| Koruma | Yeni kutuların kapladığı her hücrede `ForgeEventFactory.onBlockPlace` (hava snapshot'ı); kırma/silmede `BlockEvent.BreakEvent` | Claim modları bu olayları dinler (Build for Me ile aynı yol) |
| Maliyet | Oyuncunun envanterindeki o borunun bütün yığınları (önce sol el); iade envantere, sığmayan oyuncunun ayağına düşer; yaratıcı modda ne alınır ne verilir | Ek B6; Curvy `gatherStacks/giveStack` |
| Dünya değişimi | `BlockEvent.NeighborNotifyEvent` → kirli blok kümesi → seviye tick'i sonunda collision örtüşen kenarlar kırılır, malzeme kenarın blokla çakışan noktasına düşer | Ek B4. Piston gibi bildirim atmayan yollar Plan 5'in `LevelChunk.setBlockState` Mixin'iyle kapanır |

## Review Focus

1. **Testler arası sızan kıvrım verisi:** paylaşılan seviyedeki `CurveData`'da başka testin kenarı bu testin ağına
   karışmamalı. Test: Görev 4 `curvedNetworksStayApart` (iki test bölgesi, birbirine dokunmuyor).
2. **Kırılan blok borusunun yüzündeki düğüm:** ağ çökmeden o bağı yok saymalı; boru geri konunca bağ yeniden kurulmalı.
   Test: Görev 9 `pipeFaceLinkSurvivesPipeRemoval`.
3. **Aynı istekte yetersiz malzeme ve koruma birlikte:** hiçbir şey alınmamalı, dünya değişmemeli (önce koruma, sonra
   maliyet; ikisi de atomik). Test: Görev 6 `deniedPlacementTakesNothing`.
4. **Yüklü olmayan chunk'taki uç:** taşma oraya gitmemeli, tick'te ele alınmamalı. Test: Görev 5
   `unloadedCurvedEndIsSkipped` (`level.isLoaded` false dönen uç; sandık dışarıda kalır).
5. **Kenar silinince ekranı açık olan oyuncu:** menü geçersizleşmeli, kapanmalı. Test: Görev 11
   `curvedEndMenuClosesWhenEndGoes`.

---

### Görev 1: Port soyutlaması (davranış değişmez)

**Files:**
- Create: `src/main/java/com/knozyy/flowline/pipe/Port.java`, `pipe/PipeSidePort.java`
- Modify: `pipe/PipeNetwork.java` (`Target`, `Source`, `targets`, `sources`, `scan`), `pipe/PipeBlockEntity.java:337-346`
  (`operate`), `test/AuditGameTests.java:100`, `test/PipeGameTests.java:529` (`Target` kurulumu)

**Interfaces:**
- Produces:
  - `public interface Port { PipeType type(); SideConfig cfg(); BlockPos block(); Direction access(); long order(); }`
    — `block()` ucun bağlı olduğu blok, `access()` o bloğun hangi yüzünden erişildiği, `order()` hedef sıralamasında eşit
    mesafeleri bozan kararlı anahtar.
  - `public record PipeSidePort(PipeBlockEntity pipe, Direction side) implements Port` — `block = pipe.pos.relative(side)`,
    `access = side.getOpposite()`, `order = pos.asLong()·8 + side.ordinal()` (bugünkü sıralama anahtarı).
  - `PipeNetwork.Target(Port port, int distance, Caps caps)` — `insert()` = `port.cfg()`, `endpointPos()` = `port.block()`,
    `access()` = `port.access()`. `path` alanı kalkar (kullanan yok; animasyon 0.3.5'te kaldırıldı).
  - `PipeNetwork.Source(Port port, Caps caps)` — `cfg()` = `port.cfg()`.
  - `static List<Target> PipeNetwork.targets(ServerLevel level, Port origin, SideConfig cfg)` — önbellek anahtarı
    `origin.order()`.

- [ ] **Step 1:** Var olan 123 test bu refaktörün güvenlik ağıdır; önce yeni bir failing test yaz:
  `PipeGameTests.pipeSidePortDescribesTheSide`: sandığa bağlı borunun batı yüzü için `new PipeSidePort(be, WEST)` →
  `block() == pos.west()`, `access() == EAST`, `cfg() == be.side(WEST)`, `order() == pos.asLong()*8 + WEST.ordinal()`.
- [ ] **Step 2:** Derleme hatası beklenir (`PipeSidePort` yok).
- [ ] **Step 3:** `Port`, `PipeSidePort`'u yaz; `Target`/`Source`/`targets`/`scan`/`sources`'ı porta çevir; iki test
  kurulumunu `new Target(new PipeSidePort(pipe, Direction.EAST), 0, caps)` yap.
- [ ] **Step 4: Doğrulama** (izin al): `All 124 required tests passed`.
- [ ] **Step 5: Commit** — `Describe pipe ends as ports`

### Görev 2: Paylaşılan yan tick'i ve yükseltme kuralları (davranış değişmez)

**Files:**
- Create: `pipe/SideTick.java`, `pipe/SideUpgrades.java`
- Modify: `pipe/PipeBlockEntity.java` (`serverTick` gövdesi, `accepts`, `syncUpgrades`)
- Test: `test/PipeGameTests.java`

**Interfaces:**
- Consumes: `Port`, `PipeNetwork.targets(level, Port, cfg)` (Görev 1).
- Produces:
  - `static void SideTick.tick(ServerLevel level, Port port, BooleanSupplier signal)` (yapılandırma `port.cfg()`) — bugünkü
    `serverTick` döngüsünün tek yan için gövdesi (pulse, uyku, ilk tick dağıtımı, `Pacing`, `operate`, `signalAfter`
    sonuçları `cfg.lastMoved/lastBlocked/signalling`). `signal` yalnız kızıltaş modu `IGNORED` değilse çağrılır.
  - `static boolean SideUpgrades.accepts(SideConfig cfg, ItemStack stack)` (EXTRACT her yükseltmeyi, INSERT yalnız FILTER)
  - `static void SideUpgrades.sync(SideConfig cfg, Container upgrades, int firstSlot)` (Speed/Stack/Filter sayıları,
    değişince `cfg.wake()`).

- [ ] **Step 1: Failing test** `PipeGameTests.sideUpgradesCountLikeTheBlockPipe`: 6 yuvalık `SimpleContainer`'a 2 Speed +
  1 Filter koy, `SideUpgrades.sync(cfg, c, 0)` → `cfg.speedCount == 2`, `cfg.filterCount == 1`;
  `SideUpgrades.accepts` INSERT yanda Speed için false, Filter için true.
- [ ] **Step 2:** Derleme hatası beklenir.
- [ ] **Step 3:** Sınıfları yaz; `PipeBlockEntity` bunları çağırır (`serverTick` her yan için `SideTick.tick(level,
  new PipeSidePort(this, dir), () -> level.hasNeighborSignal(worldPosition))`; `signal` her tick'te en çok bir kez
  hesaplanır, bugünkü gibi). Kızıltaş çıkışı (`emitting`) `PipeBlockEntity`'de kalır.
- [ ] **Step 4: Doğrulama** (izin al): `All 125 required tests passed`.
- [ ] **Step 5: Commit** — `Share side ticking and upgrade rules`

### Görev 3: Kıvrımlı dünya verisi

**Files:**
- Create: `curved/Anchor.java`, `curved/CurvedEnd.java`, `curved/EdgeInfo.java`, `curved/CurveData.java`
- Create (test): `test/CurveDataGameTests.java`, `test/CurveTestSupport.java`

**Interfaces:**
- Consumes: `CurveGraph`, `CurveNode`, `CurveEdge`, `SolvedSpan`, `EndCondition` (Plan 2); `Port`, `SideUpgrades` (1–2).
- Produces:
  - `sealed interface Anchor permits Anchor.Free, Anchor.PipeFace, Anchor.BlockFace` —
    `record Free()`, `record PipeFace(BlockPos pos, Direction face)`, `record BlockFace(BlockPos pos, Direction face)`.
    `static Anchor.FREE`.
  - `final class CurvedEnd implements Port` (yalnız `BlockFace` düğümlerinde): `CurvedEnd(long nodeId, PipeType type,
    BlockFace anchor)`, `long nodeId()`, `Container upgrades()` (6 yuva, `SideUpgrades.accepts` kuralı, değişince
    `SideUpgrades.sync` ve `CurveData.setDirty`), `boolean powered` (pulse kenarı), `CompoundTag save()`,
    `static CurvedEnd load(...)`. `block()` = anchor bloğu, `access()` = anchor yüzü, `order()` =
    `Long.MAX_VALUE − nodeId`.
  - `record EdgeInfo(PipeType type, int color, EndCondition start, EndCondition end)` — yeniden çözüm için kenarın
    kurulduğu uç koşulları.
  - `final class CurveData extends SavedData`:
    - `static CurveData get(ServerLevel level)` (`computeIfAbsent`, ad `"flowline_curved_pipes"`)
    - okuma: `CurveGraph graph()`, `Anchor anchor(long node)`, `Optional<CurvedEnd> end(long node)`,
      `EdgeInfo info(long edge)`, `OptionalLong nodeAtPipeFace(BlockPos pos, Direction face)`,
      `List<Long> nodesAnchoredTo(BlockPos pos)`, `Collection<CurvedEnd> ends()`
    - yazma (hepsi `setDirty()`; Görev 4'ten sonra ağ geçersizleştirmesi, Görev 10'dan sonra senkron kuyruğu):
      `long newNodeId()`, `long newEdgeId()`, `void putNode(CurveNode node, Anchor anchor)`,
      `void putEdge(CurveEdge edge, EdgeInfo info)`, `void removeEdge(long id)`,
      `List<ItemStack> removeNode(long id)` (yükseltmeleri döndürür), `List<ItemStack> setAnchor(long node, Anchor a)`
      (`BlockFace`'ten başka bir şeye geçişte ucun yükseltmelerini döndürür, yeni `BlockFace`'te yeni `CurvedEnd` açar)
    - `CompoundTag save(CompoundTag)`, `static CurveData load(CompoundTag)`.
  - `CurveTestSupport` (test paketi): `static long node(GameTestHelper h, Vec3 rel, Anchor anchor)`,
    `static long edge(GameTestHelper h, long from, long to, PipeType type, EndCondition start, EndCondition end)`
    (`CurveSolver` ile çözüp `CurveData`'ya koyar; çözüm yoksa test başarısız), `static void clear(GameTestHelper h)`
    (test yapısının sınırları içindeki bütün düğüm ve kenarları siler). Konumlar ve anchor blokları test göreli verilir,
    yardımcı `helper.absolutePos`/`absoluteVec` ile çevirir.

Kayıt biçimi:

```text
{version:1, nextNode:long, nextEdge:long,
 nodes:[{id, pos:[x,y,z] (double), force:bool, anchor:{kind:"free"|"pipe"|"block", pos:long, face:int}, end:{cfg, upgrades}}],
 edges:[{id, from, to, type:"item"…, color:int, step:double, points:long[] (x,y,z bitleri sırayla),
         start:[x,y,z]?, end:[x,y,z]?}]}
```

- [ ] **Step 1: Failing GameTest'leri yaz** (`CurveDataGameTests`)

```java
// curveDataRoundTripsExactly: iki düğüm (biri BlockFace, biri Free), aralarında bir kenar (Görev 5 dirseği gibi);
//   uçta mode=EXTRACT, bir filtre kuralı, 1 Speed yükseltmesi. save -> load: düğüm konumları, kenar noktaları
//   (Double.compare == 0, bit bit), step, type, color, EdgeInfo yönleri, ucun SideConfig'i ve yükseltmesi aynı
// newIdsNeverRepeat: newNodeId/newEdgeId art arda farklı; save/load sonrası da öncekilerden büyük
// removingAnchoredNodeReturnsUpgrades: BlockFace düğümün ucunda 2 yükseltme -> removeNode 2 eşya döndürür
// detachingDropsTheEndsUpgrades: setAnchor(BlockFace -> FREE) yükseltmeleri döndürür, end(node) boş olur;
//   setAnchor(FREE -> BlockFace) yeni boş uç açar
// legacyCurveArchiveIsUntouched: CurveData.get(level) çağrısı "flowline_curves" verisini oluşturmaz/yazmaz
//   (getDataStorage().get(..., "flowline_curves") hâlâ null)
```

- [ ] **Step 2:** Derleme hatası beklenir.
- [ ] **Step 3:** Sınıfları ve `CurveTestSupport`'u yaz.
- [ ] **Step 4: Doğrulama** (izin al): `All 130 required tests passed`.
- [ ] **Step 5: Commit** — `Store curved pipes per world`

### Görev 4: Ağ kıvrımlı borulardan geçer

**Files:**
- Modify: `pipe/PipeNetwork.java` (graf yapısı, `build`, `scan`, `invalidate`), `pipe/PipeBlock.java:118-139`
  (`withConnections`), `curved/CurveData.java` (yazma işlemleri ağı geçersizleştirir), `tools/gen/gametest.py` (`curve`
  yapısı 9×6×9)
- Generated: `src/main/resources/data/flowline/structures/curve.nbt`
- Test: `test/CurveNetworkGameTests.java`

**Interfaces:**
- Consumes: `CurveData` (Görev 3), `Port`/`Target` (Görev 1).
- Produces:
  - `static void PipeNetwork.invalidateNode(Level level, long nodeId)` — o düğümü içeren grafı düşürür.
  - `PipeNetwork.Graph`: bloklara ek olarak kıvrımlı düğüm kümesi; `MAX_NETWORK_SIZE` blok + düğüm sayısını sınırlar.
  - `PipeBlock.withConnections`: yüzde `CurveData.nodeAtPipeFace(pos, dir)` varsa, düğümün kenarlarından biri aynı
    türde ve renkleri uyuşuyorsa `Conn.PIPE`.

Tarama (karar):

```text
köşe = blok borusu (BlockPos) | kıvrımlı düğüm (id)
flood: blok borusunun Conn.PIPE yüzü -> komşu blok; yüzde PipeFace düğümü varsa -> o düğüm
       düğüm -> aynı türde, colorsMatch kenarlarının diğer ucu (iki düğümün hücresi isLoaded ise)
       PipeFace düğümü -> o yüzün blok borusu (aynı tür, colorsMatch)
       BlockFace düğümü -> CurvedEnd portu, type.hasEndpoint(level, anchor.pos, anchor.face) ise uç
scan: Dijkstra; blok–blok ve blok–PipeFace düğümü ağırlık 1, kenar ağırlığı max(1, shape.cost()) (bkz. EdgeInfo);
      hedef sırası (distance, port.order(), …) — yalnız bloklu ağda bugünkü BFS sırasıyla aynı
```

`CurveData`'nın her yazma işlemi, değişen düğümler için `invalidateNode`'u ve `PipeFace` düğümlerinin borusu için
`PipeBlock.updateConnections`'ı çağırır.

- [ ] **Step 1: Failing GameTest'leri yaz** (`CurveNetworkGameTests`, şablon `curve`)

```java
// singleExtractDrivesTheCurvedLine: sandık A (0,1,4) — blok borusu (1,1,4) batı yüzü EXTRACT;
//   borunun doğu yüzünde PipeFace düğümü; kıvrımlı kenar (yukarı kavisli, EndCondition yüz normalleri) sandık B'nin
//   (7,1,4) batı yüzündeki BlockFace düğümüne; A'ya 8 elmas -> B'de 8 elmas (spec'in Plan 3 kabulü)
// pipeFaceTurnsIntoAPipeConnection: kenar eklenince borunun doğu yüzü Conn.PIPE; kenar silinince Conn.NONE
// curvedDistanceCountsTheLength: aynı kaynaktan iki hedef: 2 blok borusu uzaklıkta sandık ve uzun kıvrımın sonundaki
//   sandık; NEAREST dağıtımda yakın olana önce gider (ilk işlemde yalnız o dolar)
// removedEdgeStopsTheFlow: akış başladıktan sonra kenarı CurveData.removeEdge ile sil -> B'deki sayı artmaz
// curvedNetworksStayApart: iki ayrı test bölgesi (x ve z'de 4 blok ayrık) kendi kıvrımlarıyla; A1'in eşyası yalnız B1'e
```

- [ ] **Step 2:** Testler kırmızı: `curve` şablonu yok / akış olmuyor (koşu ve log ile gör).
- [ ] **Step 3:** `gametest.py`'ye `curve` yapısını ekle, `gen_resources.py` çalıştır (ilgisizleri geri al); ağı genelleştir.
- [ ] **Step 4: Doğrulama** (izin al): `All 135 required tests passed`.
- [ ] **Step 5: Commit** — `Carry networks through curved pipes`

### Görev 5: Kıvrımlı uçlar çalışır

**Files:**
- Create: `curved/CurveTicker.java` (`TickEvent.LevelTickEvent`, `Phase.END`, sunucu)
- Test: `test/CurveNetworkGameTests.java`

**Interfaces:**
- Consumes: `SideTick.tick` (Görev 2), `CurveData.ends()` (Görev 3).
- Produces: her sunucu seviyesi tick'inde `mode == EXTRACT` olan, düğüm hücresi yüklü ve `hasEndpoint` olan her
  `CurvedEnd` için `SideTick.tick(level, end, () -> level.hasNeighborSignal(düğüm hücresi))`. Pulse için
  `end.powered` kenar algılaması tick içinde yapılır. Diğer uçların çalışma alanı `resetRuntime()` ile temizlenir.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// curvedEndExtractsIntoBlockPipes: sandık A'nın yüzündeki BlockFace ucu EXTRACT; kıvrım blok borusunun yüzüne;
//   boru sandık B'ye bağlı (INSERT) -> 8 elmas B'ye geçer
// curvedEndFollowsRedstone: uç redstone=HIGH (sinyalle çalış); düğüm hücresinin yanındaki bloğa kızıltaş bloğu
//   konmadan hiçbir şey geçmez, konunca geçer
// unloadedCurvedEndIsSkipped: uç düğümü yüklü olmayan bir chunk'a (testten 2000 blok uzağa) konmuş kayıt;
//   tick hatasız sürer ve ucun cfg.interval'ı -1'de kalır (hiç tick almadı)
```

- [ ] **Step 2:** Kırmızı: uçlar tick almıyor (koşu ve log ile gör).
- [ ] **Step 3:** `CurveTicker`'ı yaz.
- [ ] **Step 4: Doğrulama** (izin al): `All 138 required tests passed`.
- [ ] **Step 5: Commit** — `Run curved pipe ends`

### Görev 6: Sunucuda yerleştirme

**Files:**
- Create: `curved/CurveEdits.java`, `curved/PlaceRequest.java`, `curved/EditResult.java`, `curved/WorldShapes.java`,
  `curved/CurveCost.java`
- Create (test, tek dinleyici): `test/TestProtection.java`
- Modify: `tools/gen/lang.py` (hata metinleri), generated `lang/{en_us,tr_tr}.json`
- Test: `test/CurveEditGameTests.java`

**Interfaces:**
- Consumes: `CurveSolver`, `PlacementValidator`, `PlacementCandidate`, `FaceAttachment`, `Reach`, `CostPlan`, `PlaceError`
  (Plan 2); `CurveData`, `Anchor`, `EdgeInfo` (Görev 3).
- Produces:
  - `record PlaceRequest(PipeType type, End start, End end, List<Vec3> internals)` ve
    `record PlaceRequest.End(Vec3 pos, OptionalLong node, Optional<Face> face)`, `record Face(BlockPos pos, Direction dir)`.
  - `record EditResult(@Nullable PlaceError error, boolean denied, int missing)`: `static EditResult OK`,
    `static EditResult error(PlaceError)`, `static EditResult DENIED`, `static EditResult missing(int)` (`TOO_COSTLY` +
    eksik adet), `boolean ok()`.
  - `final class WorldShapes implements BlockShapes` (`ServerLevel` üzerinden; `getCollisionShape`/`getShape` kutuları
    `toAabbs()` ve konuma taşınır; yüklü olmayan hücre boş liste).
  - `final class CurveCost`: `static int available(ServerPlayer p, PipeType type)`, `static void take(ServerPlayer p,
    PipeType type, int count)` (önce sol el), `static void give(ServerPlayer p, PipeType type, int count)` (sığmayan
    ayağa düşer); yaratıcı modda `available` = `Integer.MAX_VALUE`, `take/give` hiçbir şey yapmaz.
  - `static EditResult CurveEdits.place(ServerPlayer player, PlaceRequest request)`.

`place` sırası (ek B1/B6; ilk hata döner, hiçbir şey değişmez):

```text
2  sol eldeki eşya request.type'ın borusu değil; var olan düğüm kimliği yok ya da kenarları başka tür/renk;
   blok borusu yüzü dolu (o yüzde düğüm var) ya da boru başka tür  → INVALID_OBJECT
4  her uç/iç nokta ve var olan düğüm konumu Reach.tooFar(eye, getBlockReach()) → TOO_FAR
   uç -> düğüm ve koşul: face bir blok borusundaysa PipeFace (konum yüz merkezine oturur, yön yüz normali);
   başka blok yüzündeyse BlockFace (yön yüz normali, attachment olur); var olan tek kenarlı düğüm uzatma
   (başta −exitDirection, sonda exitDirection); var olan çok kenarlı ya da force düğüm joint (FREE); yoksa FREE
1  CurveSolver.solve(düğümler, DIAMETER, start, end) empty → INVALID_SHAPE
5–8 PlacementValidator.validate(graph, aday, WorldShapes)
   koruma: yeni kutuların kapladığı her hücrede ForgeEventFactory.onBlockPlace iptal → DENIED
3  CostPlan.required(yeni açıklıklar, []) > CurveCost.available → missing(eksik)
   uygula: CurveCost.take; düğümler ve kenarlar CurveData'ya (EdgeInfo: tür, renk NO_COLOR — boyama Plan 4 —, uç koşulları)
```

Lang (EN / TR, Flowline'ın kendi cümleleri): `message.flowline.curve.invalid_shape` "That curve has no valid shape" /
"Bu kıvrımın geçerli bir şekli yok"; `invalid_object` "That cannot be connected" / "Buna bağlanamaz"; `too_costly`
"Not enough pipes: %s more needed" / "Boru yetmiyor: %s tane daha gerekli"; `too_far` "Too far away" / "Çok uzak";
`intersect_block` "Pipes cannot go through blocks" / "Boru blokların içinden geçemez"; `intersect_pipe` "Pipes cannot
cross" / "Borular kesişemez"; `angle_too_small` "Pipes at a joint are too close together" / "Birleşmedeki borular birbirine
çok yakın"; `node_in_joint` "That point is inside a joint" / "O nokta bir birleşmenin içinde";
`message.flowline.curve.denied` "You cannot build here" / "Buraya kuramazsın".

- [ ] **Step 1: Failing GameTest'leri yaz** (`CurveEditGameTests`; oyuncu `helper.makeMockServerPlayerInLevel()`,
  sol eline boru verilir, göz konumu test bölgesinde; `TestProtection.denyAll` test bayrağı)

```java
// placeChargesAndBuilds: survival, sol elde 16 eşya borusu; sandık yüzünden boru yüzüne yerleştir -> ok, envanterden
//   cost kadar eksilir, CurveData'da 1 kenar, borunun yüzü Conn.PIPE
// placeWithoutEnoughPipesTakesNothing: 1 boru varken uzun kıvrım -> TOO_COSTLY, missing == cost−1, hiçbir şey değişmez
// placeTooFarIsRefused: uç noktası gözden reach+1.5 uzakta -> TOO_FAR
// placeThroughBlockIsRefused: kıvrım yolunda taş -> INTERSECT_BLOCK, envanter aynı
// placeOnOtherPipeTypeIsRefused: sıvı borusunun yüzüne eşya kıvrımı -> INVALID_OBJECT
// deniedPlacementTakesNothing: TestProtection.denyAll=true ve 1 boru -> DENIED (maliyetten önce), hiçbir şey değişmez
// placeExtendsAnEnd: var olan kenarın serbest ucundan devam -> yeni kenarın ilk segmenti eski kenarın son segmentiyle
//   aynı yönde (dot > 1−1e-9)
```

- [ ] **Step 2:** Derleme hatası beklenir.
- [ ] **Step 3:** Sınıfları yaz; lang metinlerini ekle ve üret.
- [ ] **Step 4: Doğrulama** (izin al): `All 145 required tests passed`.
- [ ] **Step 5: Commit** — `Place curved pipes on the server`

### Görev 7: Kırma ve düğüm silme

**Files:**
- Modify: `curved/CurveEdits.java`
- Test: `test/CurveEditGameTests.java`

**Interfaces:**
- Consumes: Görev 6.
- Produces:
  - `static EditResult CurveEdits.breakEdge(ServerPlayer player, long edgeId)`: kenar yok → `INVALID_OBJECT`; kenarın
    en yakın kutusu gözden `reach+1`'den uzak → `TOO_FAR`; kutuların ilk hücresinde `BreakEvent` iptal → `DENIED`;
    kenarı sil, `cost`'u iade et, kenarı kalmayan düğümleri sil (yükseltmeleri düğüm konumunda düşür).
  - `static EditResult CurveEdits.removeNode(ServerPlayer player, long nodeId)`: düğüm yok → `INVALID_OBJECT`; reach;
    koruma; tek kenar → `breakEdge`; iki kenar → iki uzak ucu birleştiren tek kenar (iki kenarın dış uç koşullarıyla
    yeniden çözülür, doğrulanır), maliyet farkı `CostPlan` ile; üç ve daha çok kenar → `INVALID_OBJECT`.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// breakRefundsTheCost: yerleştir sonra kır -> envanter başlangıçtaki sayıya döner, kenar ve uç düğümleri yok,
//   borunun yüzü Conn.NONE
// breakDropsEndUpgrades: BlockFace ucuna 2 yükseltme tak, kenarı kır -> uç konumunda 2 yükseltme eşyası (ItemEntity)
// creativeBreakGivesNothing: yaratıcı oyuncu kırınca envantere boru eklenmez
// removeMiddleNodeMergesEdges: 3 düğümlü zincirin ortasını sil -> tek kenar, uçlar aynı, maliyet farkı uygulanmış
// removeJointIsRefused: 3 kenarlı düğüm -> INVALID_OBJECT
```

- [ ] **Step 2:** Derleme hatası beklenir.
- [ ] **Step 3:** İşlemleri yaz.
- [ ] **Step 4: Doğrulama** (izin al): `All 150 required tests passed`.
- [ ] **Step 5: Commit** — `Break curved pipes and remove nodes`

### Görev 8: Ekleme, taşıma, force joint

**Files:**
- Modify: `curved/CurveEdits.java`
- Test: `test/CurveEditGameTests.java`

**Interfaces:**
- Consumes: Görev 6–7, `RayHit` (`along`) anlamı (Plan 2).
- Produces:
  - `static EditResult CurveEdits.insert(ServerPlayer p, long edgeId, int segment, double along)`: kenar yok,
    `segment` aralık dışı ya da `along ∉ [0,1]` → `INVALID_OBJECT` (ek B1); nokta reach; kenar iki açıklıklı zincir olarak
    (kenarın `EdgeInfo` koşullarıyla, ortada yeni FREE düğüm) yeniden çözülür ve iki kenarla değiştirilir; doğrulama;
    koruma; maliyet farkı.
  - `static EditResult CurveEdits.translate(ServerPlayer p, long nodeId, Vec3 pos, Optional<Face> face)`: düğüm yok →
    `INVALID_OBJECT`; reach; düğüme değen her kenar yeni konumla kendi koşullarıyla yeniden çözülür; taşınan düğümde koşul
    yeni yüze göre (yüz yoksa FREE); `BlockFace`'ten ayrılınca `setAnchor(FREE)` ve yükseltmeler düşer (ek B5);
    doğrulama, koruma, maliyet farkı.
  - `static EditResult CurveEdits.forceJoint(ServerPlayer p, long nodeId, boolean desired)`: düğüm yok → `INVALID_OBJECT`;
    reach; yeni bayrakla `PlacementValidator` (7/8) → hata ise değişmez.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// insertSplitsAnEdge: kenarın 4. segmentine along 0.5 ile ekle -> iki kenar, ortak düğüm eski segmentin ortasının
//   0.2 yakınında, toplam maliyet değişimi envantere yansımış
// insertOutsideTheEdgeIsRefused: along 1.5 -> INVALID_OBJECT; segment 999 -> INVALID_OBJECT
// translateReshapesTouchingEdges: serbest ucu 1 blok yukarı taşı -> kenarın son noktası yeni konumda
// translateOffTheFaceDetaches: BlockFace ucunu havaya taşı -> anchor FREE, ucun yükseltmesi eşya olarak düşer,
//   end(node) boş
// forceJointGrowsTheJoint: iki kenarlı düğümü force -> bayrak açık; force bir kenarın karşı ucunu R·√2 içine düşürüyorsa
//   NODE_IN_JOINT ve bayrak kapalı kalır
```

- [ ] **Step 2:** Derleme hatası beklenir.
- [ ] **Step 3:** İşlemleri yaz.
- [ ] **Step 4: Doğrulama** (izin al): `All 155 required tests passed`.
- [ ] **Step 5: Commit** — `Insert, move and force curved nodes`

### Görev 9: Dünya değişimlerinde kırılma

**Files:**
- Create: `curved/CurveWorldEvents.java` (`BlockEvent.NeighborNotifyEvent` → `CurveData.markDirty`)
- Modify: `curved/CurveData.java` (`void markDirty(BlockPos pos)`, `void processDirty(ServerLevel level)`),
  `curved/CurveTicker.java` (tick sonunda `processDirty`)
- Test: `test/CurveWorldGameTests.java`

**Interfaces:**
- Consumes: `CurveGraph.boxesNear`, `Obb.overlaps(Aabb)` (Plan 2), `WorldShapes` (Görev 6).
- Produces: `processDirty` (ek B4): her kirli hücre için o hücrenin collision kutularıyla örtüşen kenarları kırar
  (malzeme `cost` kadar boru eşyası, örtüşen kutunun merkezine düşer), kenarı kalmayan düğümleri siler (yükseltmeler
  düşer), o hücreye bağlı düğümler için `invalidateNode`. `PipeFace`/`BlockFace` bağları silinmez (bkz. kararlar).

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// blockPlacedInTheCurveBreaksIt: kıvrımın geçtiği hücreye setBlock(taş) -> sonraki tick'te kenar yok, o hücrede
//   cost kadar boru eşyası
// blockBesideTheCurveKeepsIt: kıvrıma değen ama örtüşmeyen hücreye taş -> kenar kalır
// pipeFaceLinkSurvivesPipeRemoval: PipeFace düğümlü borunun bloğunu kır -> kenar ve düğüm kalır, ağ çökmez (tick sürer);
//   boruyu geri koy -> yüz yeniden Conn.PIPE ve akış sürer
// endKeepsItsSettingsWhenTheChestGoes: BlockFace ucu EXTRACT; sandığı kır, geri koy -> uç hâlâ EXTRACT ve çekiyor
```

- [ ] **Step 2:** Kırmızı (koşu ve log).
- [ ] **Step 3:** Yaz.
- [ ] **Step 4: Doğrulama** (izin al): `All 159 required tests passed`.
- [ ] **Step 5: Commit** — `Break curved pipes when the world fills their path`

### Görev 10: Paketler ve chunk senkronu

**Files:**
- Create: `network/curve/` altında C2S `CurvePlacePayload`, `CurveBreakPayload`, `CurveInsertPayload`,
  `CurveRemovePayload`, `CurveTranslatePayload`, `CurveForceJointPayload`, `CurveEndPayload` (Görev 11 kullanır);
  S2C `CurveResultPayload`, `CurveEdgesPayload`, `CurveForgetPayload`
- Create: `curved/CurveSync.java` (değişiklik kuyruğu, `ChunkWatchEvent.Watch/UnWatch`, tick sonunda gönderim),
  `client/ClientCurves.java` (istemci deposu)
- Modify: `network/ModNetwork.java` (kayıtlar, `PROTOCOL = "12"`), `curved/CurveData.java` (yazmalar `CurveSync`'e
  bildirir)
- Test: `test/CurveSyncGameTests.java`

**Interfaces:**
- Consumes: Görev 3–8.
- Produces:
  - Her C2S paketi `encode/decode` + sunucu işleyicisi ilgili `CurveEdits` işlemini çağırır ve sonucu
    `CurveResultPayload(int requestId, int code, boolean denied, int missing)` ile döner (`code` = `PlaceError.code()`,
    0 = başarı).
  - `CurveEdgesPayload(ChunkPos chunk, List<EdgeSync> edges, List<NodeSync> nodes, List<Long> removedEdges,
    List<Long> removedNodes)`; `EdgeSync(long id, long from, long to, PipeType type, int color, double step,
    List<Vec3d> points)`, `NodeSync(long id, Vec3d pos, boolean force, byte anchorKind, boolean extract)`.
    `CurveForgetPayload(ChunkPos)`: istemci o chunk'tan gelen bilgiyi bırakır.
  - `static Set<ChunkPos> CurveSync.chunksOf(CurveEdge edge)` — kenar kutularının kapladığı chunk sütunları.
  - `ClientCurves`: `CurveGraph graph()`, `NodeSync node(long)`, `apply(CurveEdgesPayload)`, `forget(ChunkPos)`;
    bir kenar, gönderildiği chunk'ların hepsi unutulunca silinir. Çizim Plan 4.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// curvePayloadsRoundTrip: her C2S/S2C paketini FriendlyByteBuf'a yaz, oku -> alanlar eşit (noktalar bit bit)
// edgeChunksCoverItsBoxes: 0..40 x boyunca kenar -> chunksOf == {(0,0),(1,0),(2,0)}
// syncQueuesChangedEdges: CurveData'ya kenar ekle -> CurveSync kuyruğunda o kenarın chunk'ları; tick sonrası boş
// clientStoreForgetsAfterLastChunk: ClientCurves'e iki chunk'tan aynı kenar gelsin; bir chunk unutulunca kalır,
//   ikincisi unutulunca gider (istemci sınıfı sunucuda saf veri olarak denenir; render yok)
```

- [ ] **Step 2:** Derleme hatası beklenir.
- [ ] **Step 3:** Yaz. `ClientCurves` istemci paketindedir ama yalnız `curve` ve ağ türlerine dayanır, bu yüzden sunucu
  GameTest'inde denenebilir; `DistExecutor` yalnız işleyicide kullanılır.
- [ ] **Step 4: Doğrulama** (izin al): `All 163 required tests passed`.
- [ ] **Step 5: Commit** — `Sync curved pipes to clients`

### Görev 11: Kıvrımlı ucun ekranı ve anahtarı

**Files:**
- Create: `menu/SideHost.java`, `curved/CurvedEndHost.java`
- Modify: `menu/PipeMenu.java` (yapılandırıcılar, `openSide`, `stillValid`, yükseltme yuvaları, kızıltaş komşu
  güncellemesi), `pipe/PipeBlock.java:334-351` (`openConfig` → `SideHost`), `item/FilterCardItem.java`, `curved/CurveEdits.java`
  (`openEnd`, `cycleEnd`), `network/curve/CurveEndPayload.java` (işlem: OPEN | CYCLE)
- Test: `test/CurveEditGameTests.java`

**Interfaces:**
- Consumes: `CurvedEnd` (Görev 3), `CurveEndPayload` (Görev 10).
- Produces:
  - `interface SideHost { Port port(); Container upgrades(); int upgradeSlot(int index); boolean accepts(ItemStack s);
    boolean stillValid(Player p); void changed(); boolean hasSideTabs(); void writeOpenData(FriendlyByteBuf buf); }`
    — blok borusu yüzü için `PipeBlockEntity` tarafında bir uygulama, kıvrımlı uç için `CurvedEndHost`
    (`stillValid`: uç hâlâ var, oyuncu düğüme 8 blok içinde; `hasSideTabs` false).
  - `PipeMenu(int id, Inventory inv, SideHost host)` sunucu yapılandırıcısı; açılış verisi önce tür baytı
    (0 blok yüzü: pos, side; 1 kıvrımlı uç: nodeId, anchor pos, anchor face), sonra tür ve mod. İstemcide kıvrımlı uç için
    `pos` = anchor bloğu, `side` = anchor yüzü (sekme yok, başlık yüz adı; ekran cilası Plan 4).
  - `static EditResult CurveEdits.openEnd(ServerPlayer p, long nodeId)` (uç yoksa ya da blokta yetenek yoksa
    `message.flowline.no_endpoint`), `static EditResult CurveEdits.cycleEnd(ServerPlayer p, long nodeId)` (anahtar
    Shift + sağ tık: INSERT ↔ EXTRACT; blok yüzündeki "bağlantısız" adımı kıvrımlı uçta yoktur, bağı kesmek kenarı
    kırmaktır).
  - `static EditResult CurveEdits.useCardOnEnd(ServerPlayer p, long nodeId, InteractionHand hand)`: Filter Card'ı
    blok yüzündeki gibi uygular (Shift ile kopyala, değilse yapıştır). `FilterCardItem.useOnPipe` bunun için
    `SideConfig` üzerinden çalışan bir yardımcıya ayrılır.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// curvedEndOpensTheSameScreen: openEnd -> player.containerMenu bir PipeMenu, cfg ucun SideConfig'i;
//   menünün mod düğmesi (clickMenuButton) ucun modunu değiştirir
// curvedEndAcceptsUpgradesLikeASide: menünün yükseltme yuvasına Speed koy -> ucun cfg.speedCount 1
// cycleEndTogglesExtract: cycleEnd iki kez -> INSERT, EXTRACT, INSERT
// curvedEndMenuClosesWhenEndGoes: ekran açıkken kenarı kır -> menu.stillValid(player) false
// filterCardWorksOnCurvedEnds: blok borusu yüzünden kart kopyala (Shift), kıvrımlı uca yapıştır -> kurallar aynı
```

- [ ] **Step 2:** Derleme hatası beklenir.
- [ ] **Step 3:** Menüyü genelleştir; blok borusu ekranı aynen çalışmalı (mevcut `GuiGameTests` güvence).
- [ ] **Step 4: Doğrulama** (izin al): önce `gradle.properties` → `mod_version=0.3.10+1.20.1`.
  `.\gradlew.bat build --console=plain` → exit 0; `runGameTestServer` → `All 168 required tests passed`;
  `runGameTestServer -PwithMekanism` → aynı sayı.
- [ ] **Step 5: Commit** — `Open curved pipe ends like pipe sides`

---

## Plan 3 kapanışı

- Kovan `30-Hafiza/Projeler/Flowline.md`: commit'ler, test sayısı, kararlar (özellikle "bağlı blok kırılınca uç kalır"
  sapması kullanıcı onayı bekliyor diye).
- Plan 4 (E: istemci) bu planın Interfaces bloklarından yazılır: `ClientCurves` verisiyle kare cam kanal mesh'i ve cam
  düğüm, önizleme (`CurveSolver`/`PlacementValidator` istemcide), HUD, tuşlar, 6 grid modu, seçenek çarkı, paketlerin
  gönderimi ve `CurveResultPayload` mesajları; kıvrımlı uç ekranının cilası; boyama.
