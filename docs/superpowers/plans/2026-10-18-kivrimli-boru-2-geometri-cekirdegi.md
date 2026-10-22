# Kendi kıvrımlı boru motoru — Plan 2/6: Saf Java geometri çekirdeği (C)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Minecraft'tan bağımsız `com.knozyy.flowline.curve` paketini yazmak: kıvrım çözücü, kare kesitli çarpışma
geometrisi, joint/açı kuralları, maliyet, yerleşim doğrulayıcısı ve ışın/hacim sorguları. Hepsi testle doğrulanır.

**Architecture:** Çekirdek yalnız `java.*` kullanır; Plan 3 (dünya/ağ), Plan 4 (istemci) ve Plan 5 (Mixin'ler) bunun
üstüne kurulur. Çözücü sıfırdan yazılır: eşit segment kısıtı altında ağırlıklı yön farkı enerjisinin
KKT koşullarını sönümlü Newton ile çözer. Doğrusal sistem kenarlıklı bant matristir (bant: noktalar + çarpanlar,
kenarlık: açıklık başına `h`). Çarpışma her segment için yönlü kare kutu (OBB) kullanır.

**Tech Stack:** Java 17, Forge 47.3.0 (MC 1.20.1), ForgeGradle 6, Forge GameTest (testler için).

**Spec:** `docs/superpowers/specs/2026-10-18-kivrimli-boru-kendi-motor-design.md`
**Davranış kaynağı:** spec'in "Referans davranış" bölümü ve onu ayrıntılandıran ek (bundan sonra "ek"); ek bölüm
adları görevlerde belirtilir.

## Global Constraints

- Hedef: Minecraft 1.20.1, Forge 47.3.0, Java 17; dal `feature/own-curved-pipes`.
- `com.knozyy.flowline.curve` paketi `net.minecraft`, `com.mojang`, `net.minecraftforge` türlerine dokunmaz
  (Görev 1'deki denetim testi bunu zorlar).
- Flexi Pipes deposundaki kullanıcının kendi MIT kodu (`com.flexipipes.core.Vec3d`, `Aabb`) iskelet olarak alınabilir.
- Kıvrımlı borunun çapı `6/16 = 0.375`, yarıçapı `0.1875`; çözücünün örnek aralığı `s` = çap (spec "Boyut").
- Çarpışma (5, 6), varlık çarpışması ve ışınla seçme gerçek kare kesiti kullanır; açı/joint kuralları (7, 8) yuvarlak
  boru formülünü `r = 0.1875`, çap `0.375` ile korur (spec "Kare kesit ve çarpışma").
- **Testler JUnit değil GameTest'tir.** Bu proje yolu (`Yeni klasör`) Gradle'ın JUnit işçisinin classpath'ini bozuyor
  (Kovan: `10-Sorun-Bankasi/2026-10-gradle-junit-turkce-yol-classnotfound.md`). GameTest bu yolda ve CI'da çalışıyor.
  Test sınıfları `src/main/java/com/knozyy/flowline/test/` altında, `AuditGameTests` gibi
  `@GameTestHolder(Flowline.MODID) @PrefixGameTestTemplate(false)`, `@GameTest(template = "empty", timeoutTicks = 20)`;
  saf hesap yapıp `helper.assertTrue(...)` ve `helper.succeed()` çağırır. Kayan nokta karşılaştırması için her test
  sınıfında `private static boolean near(double a, double b, double eps)`.
- Doğrulama komutu (her görev): `.\gradlew.bat runGameTestServer --console=plain`. Geçme ölçütü
  `run\gametest-normal\logs\latest.log` içinde `All <N> required tests passed`. Exit 0 tek başına yeterli değil;
  Türkçe yerelde Gradle "BUİLD" yazar, "BUILD" araması boş döner. Başlangıç sayısı **75**.
- **Derleme, GameTest ve oyun başlatma kullanıcıya sorulmadan çalıştırılmaz** (oturumda verilen izin o oturumla sınırlı).
- Her yeni derlemede (`gradlew build`) `gradle.properties` `mod_version` artırılır; GameTest koşusu derleme sayılmaz.
- Commit kimliği `Knozyy <167627012+Knozyy@users.noreply.github.com>`, **atıf satırı yok**
  (`git -c user.name=Knozyy -c user.email=167627012+Knozyy@users.noreply.github.com commit ...`). Push yok.

## Review Focus

1. **İstemciden gelen NaN/sonsuz koordinat:** çözücü takılmadan reddetmeli (Plan 3 bu çekirdeği paket verisiyle
   çağıracak). Test: Görev 5 `solveRejectsNonFinite`.
2. **Çakışık düğümler (`d = 0`):** bölme hatası yerine `INVALID_SHAPE`. Test: Görev 2 `spanLayoutRejectsShortSpan`'deki
   `d = 0` iddiası.
3. **Uzun zincir (8 açıklık × 10 blok):** sunucu tick'inde kabul edilebilir sürede çözülmeli. Test: Görev 5
   `solveLongChainIsFast` (< 1000 ms).
4. **Yere yaslanan boru:** blok üst yüzüne tam değen kıvrım `intersect_block` vermemeli. Test: Görev 9
   `edgeRestingOnBlocksIsAllowed`.
5. **Kamera borunun içindeyken ışın:** ışın başlangıcı kutunun içindeyse mesafe 0 dönmeli, ıska geçmemeli. Test:
   Görev 1 `obbRayHitsFromOutsideAndInside`.

---

### Görev 1: Vektör, AABB, OBB ve paket saflığı

**Files:**
- Create: `src/main/java/com/knozyy/flowline/curve/Vec3d.java`, `Aabb.java`, `Obb.java`, `CurveRules.java`
- Create (test): `src/main/java/com/knozyy/flowline/test/CurveGeometryGameTests.java`
- Modify: `.github/workflows/*.yml` (Plan 1 kalıntısı: eski entegrasyonun dünya içi test adımı ve bayrağı silinir)

**Interfaces:**
- Produces:
  - `record Vec3d(double x, double y, double z)`: `ZERO`, `add`, `sub`, `scale(double)`, `addScaled(Vec3d,double)`,
    `dot`, `cross`, `lengthSq`, `length`, `distanceSq`, `normalize()` (uzunluk `< 1e-12` ise `ZERO`), `isFinite()`.
  - `record Aabb(double minX, minY, minZ, maxX, maxY, maxZ)`: `static Aabb of(Vec3d... points)`, `inflate(double)`,
    `union(Aabb)`, `boolean intersects(Aabb)` (kapalı aralık; geniş faz için), `boolean contains(Vec3d)`.
  - `record Obb(Vec3d center, Vec3d u, Vec3d v, Vec3d w, double hu, double hv, double hw)`: `u` segment ekseni,
    `v`, `w` kesit eksenleri (birim, dik).
    - `static Obb segment(Vec3d a, Vec3d b, Vec3d normal, double halfWidth)`: `u=(b−a)/|b−a|`, `hu=|b−a|/2`,
      `v` = `normal`'in `u`'ya dik bileşeni (birim), `w=u×v`, `hv=hw=halfWidth`.
    - `Aabb bounds()`, `boolean overlaps(Aabb box)`, `boolean overlaps(Obb other)`,
      `double rayHit(Vec3d origin, Vec3d dir, double maxDist)` (birim `dir`; ıska `Double.NaN`; içeriden 0).
  - `final class CurveRules` bu görevde yalnız `OVERLAP_EPS = 1e-6` ile başlar; Görev 2 kalan sabitleri ekler.

`overlaps` ayırıcı eksen testidir (OBB–AABB ve OBB–OBB için 15 eksen: iki kutunun 3+3 ekseni ve 9 çapraz çarpım;
uzunluğu `< 1e-12` olan çapraz eksen atlanır). Kesişme yalnız **her** eksende izdüşüm örtüşmesi `> OVERLAP_EPS` ise
vardır: değen yüzler kesişmez (ek B3: Flowline değmeyi sayısal olarak güvenli tarafa koyar).

- [ ] **Step 1: Failing GameTest'leri yaz** (`CurveGeometryGameTests`)

```java
// obbAabbTouchingFaceIsNotOverlap:
//   Obb.segment(a=(0,1.1875,0.5), b=(1,1.1875,0.5), normal (0,1,0), halfWidth 0.1875)
//   .overlaps(new Aabb(0,0,0,1,1,1)) == false   (alt yüz y=1'e tam değiyor)
//   aynı kutu y −0.01 kaydırılınca (a.y=b.y=1.1775) == true
// obbSeparatesDiagonalCorner:
//   a=(0.9,1.5,0.5), b=(1.5,0.9,0.5), normal (0,0,1), birim küp (0..1):
//   halfWidth 0.1875 -> false (köşe (1,1) doğruya 0.283 uzak, AABB'ler yine de örtüşüyor)
//   halfWidth 0.3    -> true
// obbObbParallelNeighbours:
//   a1=(0,0,0)->(2,0,0) ve a2=(0,d,0)->(2,d,0), normal (0,1,0), hw 0.1875:
//   d=0.375 -> false (değiyor), d=0.37 -> true
// obbRayHitsFromOutsideAndInside:
//   kutu a=(0,1,0.5)->(1,1,0.5), normal (0,1,0), hw 0.1875
//   rayHit((0.5,5,0.5),(0,-1,0),10) ≈ 3.8125 (1e-9); rayHit((0.5,5,0.5),(0,1,0),10) NaN;
//   rayHit((0.5,1,0.5),(0,-1,0),10) == 0  (içeriden)
// curvePackageHasNoMinecraftTypes:
//   ModList.get().getModFileById(Flowline.MODID).getFile().findResource("com","knozyy","flowline","curve")
//   altındaki her .class dosyasının baytlarında "net/minecraft", "com/mojang", "net/minecraftforge" geçmez;
//   en az bir sınıf bulunmalı
```

- [ ] **Step 2: Derleme hatası beklenir** (`com.knozyy.flowline.curve` yok). Komut: `.\gradlew.bat compileJava --console=plain`
  → `cannot find symbol`/`package ... does not exist`.
- [ ] **Step 3: `Vec3d`, `Aabb`, `Obb`'yi yaz.** `Vec3d`/`Aabb` Flexi Pipes'tan alınabilir (paket adı değişir).
  `rayHit`: kökeni ve yönü kutunun yerel eksenlerine (`u,v,w`) izdüşürüp slab testi. CI dosyasındaki eski entegrasyon adımını sil.
- [ ] **Step 4: Doğrulama** (izin al): `runGameTestServer` → `All 80 required tests passed` (75 + 5).
- [ ] **Step 5: Commit** — `Add the curve geometry primitives`

### Görev 2: Hata kodları, kurallar ve açıklık düzeni

**Files:**
- Create: `curve/PlaceError.java`, `curve/EndCondition.java`, `curve/SpanLayout.java`
- Modify: `curve/CurveRules.java` (kalan sabitler)
- Create (test): `test/CurveSolverGameTests.java`

**Interfaces:**
- Produces:
  - `enum PlaceError { INVALID_SHAPE(1), INVALID_OBJECT(2), TOO_COSTLY(3), TOO_FAR(4), INTERSECT_BLOCK(5),
    INTERSECT_PIPE(6), ANGLE_TOO_SMALL(7), NODE_IN_JOINT(8) }`: `int code()`, `static Optional<PlaceError> byCode(int)`,
    `String key()` = `"message.flowline.curve." + name().toLowerCase(Locale.ROOT)` (metinler Plan 4'te).
  - `CurveRules`'a eklenen sabitler: `DIAMETER = 6/16.0`, `RADIUS = DIAMETER / 2`, `MAX_BASE_SEGMENTS = 64`,
    `MIN_SPAN_FACTOR = 0.5`, `CONVERGED = 0.01`, `STEP = 0.5`, `FREE_ITERATIONS = 4`, `MAX_ITERATIONS = 200`,
    `REGULARISATION = 1e-8`, `FACE_TOLERANCE = 0.01`, `REACH_MARGIN = 1.0`, `ANGLE_FREE = 0.1`, `ANGLE_SAME = 0.999`,
    `JOINT_MIN_SQ = 0.25`.
  - `record EndCondition(Vec3d direction)`: `static final EndCondition FREE = new EndCondition(null)`,
    `boolean fixed()`. Yön birimdir ve **hat boyunca** bakar: başta `P0`'dan dışarı, sonda `P1`'e doğru.
  - `record SpanLayout(double length, int base, int extra)`: `int segments()` = `base + extra`,
    `double initialStep()` = `length / segments()`,
    `static Optional<SpanLayout> of(Vec3d p0, Vec3d p1, double spacing, EndCondition start, EndCondition end)`.
    `start`/`end`, bu açıklığa düşen **zincir ucu** koşullarıdır; zincirin iç düğümlerinde `FREE` verilir.

`SpanLayout.of` (ek B2 "Açıklık başına başlangıç"):

```text
d = |p1 − p0|;  d < MIN_SPAN_FACTOR·spacing (ya da d sonlu değil) → empty
A = d + (start.fixed ? d − start.dir·Δ : 0) + (end.fixed ? d − end.dir·Δ : 0)
base  = ceil(clamp(A / spacing, 1, MAX_BASE_SEGMENTS))
extra = (start.fixed ? 1 : 0) + (end.fixed ? 1 : 0)
```

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// spanLayoutStraightFreeEnds: (0,0,0)->(4,0,0), s=0.5, FREE/FREE -> base 8, extra 0, segments 8, initialStep 0.5
// spanLayoutRejectsShortSpan: d=0.2,s=0.5 -> empty; d=0.25 -> present (eşitlik geçer); d=0 -> empty
// spanLayoutBothEndsFixed: p0=(0,0,0) dir (0,1,0); p1=(2,2,0) dir (1,0,0); s=0.5
//   -> base 9, extra 2, segments 11, initialStep ≈ 2√2/11 (1e-12)
// spanLayoutClamps: (0,0,0)->(100,0,0), s=0.375 -> base 64;
//   p0=(0,0,0) dir (1,0,0), p1=(0.3,0,0) dir (1,0,0), s=0.375 -> A=0.3 -> base 1, extra 2
// placeErrorCodesAreInOrder: INVALID_SHAPE..NODE_IN_JOINT kodları 1..8; byCode(9) empty;
//   TOO_FAR.key() == "message.flowline.curve.too_far"
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: Sınıfları yaz.**
- [ ] **Step 4: Doğrulama** (izin al): `All 85 required tests passed`.
- [ ] **Step 5: Commit** — `Lay out curve spans`

### Görev 3: Zincir problemi — değişkenler, artık ve Jacobian

**Files:**
- Create: `curve/ChainProblem.java`, `curve/BorderedBandMatrix.java`
- Test: `test/CurveSolverGameTests.java`

**Interfaces:**
- Consumes: `Vec3d`, `EndCondition`, `SpanLayout` (Görev 2).
- Produces:
  - `final class BorderedBandMatrix`: `BorderedBandMatrix(int bandSize, int border, int halfBand)`,
    `int size()` (= `bandSize + border`), `int bandSize()`, `int border()`, `int halfBand()`,
    `void set(int row, int col, double v)`, `double get(int row, int col)`, `boolean holds(int row, int col)`
    (bant: `row,col < bandSize` ve `|row−col| ≤ halfBand`; ya da satır/sütundan biri kenarlıkta),
    `void addDiagonal(int i, double v)`, `void clear()`. `holds` false olan konuma `set` → `IllegalArgumentException`.
  - `final class ChainProblem`:
    - `ChainProblem(List<Vec3d> nodes, double spacing, EndCondition start, EndCondition end, List<SpanLayout> spans)`
    - `int size()`, `int bandSize()` (= nokta + çarpan değişkenleri), `int spans()`, `static final int HALF_BAND = 11`
    - `double[] initialGuess()`, `double lagrangian(double[] z)`, `double[] residual(double[] z)`,
      `void jacobian(double[] z, BorderedBandMatrix out)`, `boolean isMultiplier(int index)`,
    - `List<List<Vec3d>> points(double[] z)` (açıklık başına uçlar dahil noktalar), `double step(double[] z, int span)`.

**Değişken düzeni (karar).** Açıklık `i` için noktalar `q_{i,0}=P_i … q_{i,m}=P_{i+1}`. Bağımlı noktalar:
ilk açıklıkta `start.fixed` ise `q_{0,1} = P_0 + h_0·t0`; son açıklıkta `end.fixed` ise `q_{L,m−1} = P_N − h_L·tN`.
Geri kalan iç noktalar serbesttir. Bant vektörü segment segment dizilir: segment `j` için önce `q_{j+1}` serbestse
3 koordinatı, sonra `λ_j`. Zincir sırası açıklık 0'dan sona. Kenarlık: açıklık başına bir `h_i`, bantın arkasında.

**Fonksiyonlar (ek B2 "Denklem sistemi").** Bütün segmentler zincir sırasında dizilir; ardışık her `(a, b)` çifti için
(açıklık sınırında da) `E += 0.5·(h_a + h_b)·|e_b/h_b − e_a/h_a|²`; her segment için `c = |e|² − h²`;
`L = E + Σ λ·c`. `residual(z)` = `∇L` (z sırasıyla: nokta bileşenleri `∂L/∂q`, çarpan yuvasında `c`, kenarlıkta
`∂L/∂h`); analitik yazılır. `initialGuess`: serbest noktalar `P_i + (j/m)·(P_{i+1}−P_i)`, `h_i = initialStep`, `λ = 0`.

**Jacobian (karar).** `L`'nin Hessian'ı simetriktir; analitik yazılmaz, `residual`'ın merkezi farkıyla kurulur.
Maliyeti düşük tutmak için bant sütunları renklendirilir (birbirinden `2·HALF_BAND+1` uzak sütunlar aynı bant
satırlarına dokunmaz):

```text
δ_k = 1e-6·max(1, |z_k|)
renk c = 0 .. 2·HALF_BAND:   c, c+23, c+46, … sütunlarını birlikte ±δ ile oynat
    bant satırı r için: k = o renkte |r−k| ≤ HALF_BAND olan tek sütun → J[r][k] = (F+[r] − F−[r]) / (2δ_k)
her h_i sütunu ayrı oynatılır: bütün satırlar J[r][h_i]; kenarlık satırları J[h_i][k] = J[k][h_i] (simetri)
```

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// straightChainHasZeroResidual: nodes (0,0,0),(4,0,0), s=0.5, FREE/FREE
//   -> max |residual(initialGuess())| < 1e-12
// residualIsLagrangianGradient: dirsek zinciri (Görev 2 spanLayoutBothEndsFixed girdisi), z = initialGuess()
//   her bileşen k'ya küçük sabit sapma ekle (z_k += 0.01·sin(k+1)), λ'ları 0.1·cos(k)
//   -> |residual(z)[k] − (lagrangian(z+δe_k) − lagrangian(z−δe_k))/(2δ)| < 1e-5, δ=1e-6, her k için
// jacobianFitsBandAndIsSymmetric: aynı z; testte residual'dan yoğun merkezi fark Jacobian'ı kur
//   -> holds()==false olan her konumda |J| < 1e-9; jacobian(z,m) ile yoğun J farkı < 1e-5; m simetrik (1e-5)
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: Sınıfları yaz.** `HALF_BAND = 11`: bükülme bir noktayı iki komşusuna, kısıt iki komşu noktaya bağlar;
  4'lük bloklarla en uzak bağ `2·4+3`. Test bunu doğrular; tutmazsa yeni değer bir karar olarak deftere yazılır.
- [ ] **Step 4: Doğrulama** (izin al): `All 88 required tests passed`.
- [ ] **Step 5: Commit** — `Pose the curve chain as a KKT system`

### Görev 4: Kenarlıklı bant çözücü

**Files:**
- Create: `curve/BorderedBandSolver.java`
- Test: `test/CurveSolverGameTests.java`

**Interfaces:**
- Consumes: `BorderedBandMatrix` (Görev 3).
- Produces: `static double[] BorderedBandSolver.solve(BorderedBandMatrix m, double[] rhs)`: `M·x = rhs`'in çözümü,
  tekil ise (pivot `|p| < 1e-300`) `null`. `m` ve `rhs` değişmez.

```text
M = [[A, B], [D, C]]   (A: bandSize×bandSize bant, B: band×S, D: S×band, C: S×S)
A'yı bant LU ile ayrıştır, kısmi pivotlama (üst bant genişliği 2·halfBand'e kadar büyür)
A·Y = B ve A·y0 = rhs_band çöz
Schur: (C − D·Y)·xh = rhs_h − D·y0   (S×S yoğun, kısmi pivotlu Gauss)
x_band = y0 − Y·xh
```

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// borderedSolveMatchesDense: new Random(7); bandSize 40, border 2, halfBand 11;
//   bant içi girişler [-1,1], köşegen işaretleri dönüşümlü ±(3+rand) (belirsiz matris), kenarlık [-1,1]
//   -> testteki yoğun Gauss çözümüyle fark < 1e-9 (yoğun yardımcı test sınıfında private)
// borderedSolveNeedsPivoting: (0,0)=0, (0,1)=(1,0)=1, kalan köşegen 2, border 1 köşesi 5 -> çözüm var, M·x≈rhs (1e-12)
// borderedSolveReportsSingular: tamamen sıfır 10+1 matris -> null
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: `BorderedBandSolver`'ı yaz.**
- [ ] **Step 4: Doğrulama** (izin al): `All 91 required tests passed`.
- [ ] **Step 5: Commit** — `Solve bordered band systems`

### Görev 5: Kıvrım çözücü

**Files:**
- Create: `curve/CurveSolver.java`, `curve/SolvedSpan.java`, `curve/SolvedChain.java`
- Test: `test/CurveSolverGameTests.java`

**Interfaces:**
- Consumes: `ChainProblem`, `BorderedBandSolver`, `SpanLayout`, `CurveRules` (Görev 2–4).
- Produces:
  - `record SolvedSpan(List<Vec3d> points, double step)`: `int segments()` (= `points.size()−1`),
    `int cost()` = `(int) Math.ceil(segments() * step)` (ek "Kenar maliyeti"; segment sayısı `n+k`, uzunluk
    `segments·step`).
  - `record SolvedChain(List<SolvedSpan> spans)`.
  - `static Optional<SolvedChain> CurveSolver.solve(List<Vec3d> nodes, double spacing, EndCondition start, EndCondition end)`:
    en az 2 düğüm; boş sonuç = `INVALID_SHAPE`.
  - `static boolean CurveSolver.keepGoing(int iteration, double previous, double current)` ve
    `static boolean CurveSolver.acceptable(List<List<Vec3d>> spans, double[] steps)` (testlenebilir parçalar).

Döngü (ek B2 "Döngü ve yakınsama"):

```text
her düğüm ve yön sonlu değilse → empty;  her açıklık için SpanLayout.of (iç uçlar FREE) → biri empty ise empty
z = initialGuess();  r = Σ residual(z)²
r < CONVERGED → bitir
iteration = 0; previous = r
döngü:
    jacobian(z, m); her nokta/h köşegenine +REGULARISATION, her çarpan köşegenine −REGULARISATION ekle
    Δ = solve(m, residual(z));  Δ == null → empty
    z −= STEP·Δ;  r = Σ residual(z)²;  r sonlu değil → empty
    r < CONVERGED → bitir
    iteration ≥ MAX_ITERATIONS ya da !keepGoing(iteration, previous, r) → empty
    previous = r; iteration++
keepGoing(i, prev, cur) = i < FREE_ITERATIONS || prev > cur
bitir: acceptable(points(z), steps) değilse empty
acceptable: her h > 0 ve zincir sırasındaki ardışık her segment çifti için dot(e_b, e_a) > 0 (açıklık sınırında da)
```

`MAX_ITERATIONS = 200` Flowline'ın güvenlik sınırıdır (ek B2); "4 adım + azalma" kuralıyla sonsuz yavaş
iniş kesilir. Kabul eşiği bilerek gevşektir: `0.5` sönümlü adım her iterasyonda hatayı kabaca yarıya indirir,
döngü `r` 0.01'in altına inince durur. Çözüm bu yüzden kısıtları tam sağlamaz (`|c| < 0.1`); bu beklenen davranıştır,
"düzeltilmez". Bağımlı uç noktaları (`P0 + h·t0`) ise tanımı gereği tamdır.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// solveStraightAcceptsInitialGuess: (0,0,0)->(4,0,0), s=0.5, FREE/FREE -> 1 açıklık, 8 segment, step 0.5,
//   cost 4, bütün noktalar y=z=0 (1e-12)
// solveRejectsShortSpan: (0,0,0)->(0.2,0,0), s=0.5 -> empty
// solveElbowBothEndsFixed: p0=(0,0,0) t0=(0,1,0); p1=(2,2,0) tN=(1,0,0); s=CurveRules.DIAMETER
//   -> present; segments == SpanLayout.of(...).segments(); ilk segment yönü·t0 > 1−1e-9; son segment yönü·tN > 1−1e-9;
//   her segment için ||e|² − step²| < 0.1 (kabul eşiği gevşek: Σ F² < 0.01 ⇒ |c| < 0.1);
//   Σ|e| > |p1 − p0| (kirişten uzun); cost == ceil(segments·step)
// solveSmoothThroughInteriorNode: (0,0,0),(2,1,0),(4,0,0), FREE/FREE, s=DIAMETER -> 2 açıklık;
//   düğümdeki iki segmentin yön dot > 0.9; ayna simetrisi: her nokta (x,y) için (4−x,y) karşılığına en yakın nokta 1e-3 içinde
// solveRejectsNonFinite: (0,0,0),(NaN,0,0) -> empty; t0 = (Infinity,0,0) -> empty
// solveLongChainIsFast: 9 düğüm, x=0,10,20…80, y = 0,2,0,2…, FREE/FREE -> present ve System.nanoTime farkı < 1000 ms
// solverStopRuleHolds: keepGoing(0..3, 1, 2) true; keepGoing(4, 1, 2) false; keepGoing(4, 2, 1) true
// solverRejectsFolds: acceptable([[(0,0,0),(1,0,0),(0.5,0,0)]],[0.5]) false (geri dönüş);
//   [[(0,0,0),(1,0,0),(1,1,0)]] false (dot = 0); [[(0,0,0),(1,0,0),(1.01,1,0)]] true; step 0 olan açıklık false
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: Sınıfları yaz.**
- [ ] **Step 4: Doğrulama** (izin al): `All 99 required tests passed`.
- [ ] **Step 5: Commit** — `Solve curved pipes`

### Görev 6: Joint ve açı kuralları

**Files:**
- Create: `curve/JointRules.java`
- Create (test): `test/CurveRulesGameTests.java`

**Interfaces:**
- Produces (`final class JointRules`, ek):
  - `static double pipeRadius(double diameter)` = `0.5·diameter`
  - `static double jointRadius(double[] diameters, boolean forceJoint)` = `0.5·max(diameters)`; `diameters.length ≥ 3`
    ya da `forceJoint` ise `·√2`
  - `static boolean endOrTransition(double[] diameters, boolean forceJoint)`: `len==1 ∧ ¬force` ya da
    `len==2 ∧ ¬force ∧ d0≠d1`
  - `static boolean nodeInJoint(Vec3d node, Vec3d farEnd, double jointRadius)` = `|farEnd−node|² < R²`
  - `static boolean angleTooSmall(Vec3d a, Vec3d b, double ra, double rb, double jointRadius)`, `a`,`b` birim çıkış
    yönleri: `d=a·b`; `d ≤ ANGLE_FREE` → false; `d ≥ ANGLE_SAME` → true;
    aksi hâlde `t=(d·rb + ra)/|a×b|`, `t² + rb² > max(4R², JOINT_MIN_SQ)` → true.

- [ ] **Step 1: Failing GameTest'leri yaz** (`r = 0.1875`, `R2 = 0.1875·√2`)

```java
// jointRadiusFollowsTheRules: ([0.375],false)=0.1875; ([0.375,0.375],false)=0.1875;
//   ([0.375,0.375,0.375],false)=R2; ([0.375,0.375],true)=R2   (1e-12)
// endOrTransitionFlag: ([0.375],false) true; ([0.375],true) false; ([0.375,0.375],false) false;
//   ([0.375,0.5],false) true; 3 kenar false
// nodeInJointUsesSquaredRadius: node (0,0,0), R=0.2: far (0.19,0,0) true; (0.2,0,0) false
// angleRulesHold (R=R2, ra=rb=0.1875): a=(1,0,0)
//   b dot 0.05 -> false; b dot 0.9995 -> true; b=(cos60°,sin60°,0) -> false (t²+rb²≈0.1406 < 0.28125);
//   b dot 0.9 -> true (≈0.7031 > 0.28125)
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: `JointRules`'ı yaz.**
- [ ] **Step 4: Doğrulama** (izin al): `All 103 required tests passed`.
- [ ] **Step 5: Commit** — `Add the joint and angle rules`

### Görev 7: Kesit çerçeveleri ve kenar kutuları

**Files:**
- Create: `curve/Frames.java`, `curve/EdgeShape.java`
- Test: `test/CurveGeometryGameTests.java`

**Interfaces:**
- Consumes: `Obb.segment` (Görev 1), `SolvedSpan` (Görev 5).
- Produces:
  - `static Vec3d Frames.blockNormal(Vec3d tangent)`: `|tangent.y| > 0.9` ise `(0,0,1)`, değilse `(0,1,0)`;
    `tangent`'a dik bileşeni, birim.
  - `static List<Vec3d> Frames.normals(List<Vec3d> points)`: segment başına bir normal (segmente dik, birim). Plan 4'ün
    mesh'i aynı normalleri kullanır, böylece görünen kare ile çarpışma karesi aynıdır.
  - `static List<Obb> EdgeShape.boxes(SolvedSpan span, double halfWidth)`: segment başına `Obb.segment`.
  - `static Aabb EdgeShape.bounds(List<Obb> boxes)`.

`normals` (karar; spec "blok borusundan kıvrımlıya geçişte biçim kırılması yok"):

```text
n_0 = blockNormal(t_0)
k = 1..: n_k = n_(k−1)'i t_(k−1)'den t_k'ya götüren en küçük dönüşle döndür (paralel taşıma), sonra t_k'ya dik yap
hedef = blockNormal(t_last)
θ = t_last etrafında n_last'tan hedefe açı, kare simetrisi için en yakın 90°'ye göre (−45°, 45°] aralığına indir
segment k'yı kendi ekseni etrafında θ·k/(segments−1) döndür (tek segmentte döndürme yok)
```

İki uç da dünya eksenlerine hizalanır; bükülme hat boyunca doğrusal dağılır.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// framesStraightAlongXUseUp: (0..4 boyunca x) noktaları -> her normal (0,1,0) (1e-12)
// framesVerticalUseZ: (0,0,0)->(0,4,0) -> her normal (0,0,1)
// framesStayPerpendicular: Görev 5 dirsek çözümü -> her normal birim ve |normal·segmentYönü| < 1e-9
// framesEndsMatchBlockAxes: dirsek çözümü -> ilk normal ≈ blockNormal(ilk yön) (1e-9);
//   son normal, blockNormal(son yön) ya da onun yön etrafında 90° döndürülmüşüyle |dot| > 1−1e-9
// edgeBoxesCoverSegments: düz 8 segment (0..4, x), halfWidth 0.1875 -> 8 kutu;
//   bounds == Aabb(0,−0.1875,−0.1875,4,0.1875,0.1875) (1e-9)
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: Sınıfları yaz.**
- [ ] **Step 4: Doğrulama** (izin al): `All 108 required tests passed`.
- [ ] **Step 5: Commit** — `Frame curved pipes square to the block grid`

### Görev 8: Saf graf, uzamsal dizin ve sorgular

**Files:**
- Create: `curve/CurveNode.java`, `curve/CurveEdge.java`, `curve/CurveGraph.java`, `curve/SegmentBox.java`, `curve/RayHit.java`
- Create (test): `test/CurveGraphGameTests.java`

**Interfaces:**
- Consumes: `SolvedSpan`, `EdgeShape`, `Obb`, `Aabb`.
- Produces (Plan 3 bu grafı kalıcı veriye sarar; düzenleme işlemleri ve journal Plan 3'tür):
  - `record CurveNode(long id, Vec3d pos, boolean forceJoint)`
  - `record CurveEdge(long id, long from, long to, double diameter, SolvedSpan shape)` — `shape.points()` `from`'dan
    `to`'ya gider.
  - `record SegmentBox(long edgeId, int segment, Obb box)`
  - `record RayHit(long edgeId, int segment, double distance, Vec3d point, double along)` — `along` ∈ [0,1],
    segment üzerindeki oran (Plan 3'ün Insert `pct`'si).
  - `final class CurveGraph`: `putNode(CurveNode)`, `removeNode(long)` (kenarı varsa `IllegalStateException`),
    `putEdge(CurveEdge)` (uç düğümler yoksa `IllegalArgumentException`; aynı kimliği değiştirir ve yeniden dizinler),
    `removeEdge(long)`, `Optional<CurveNode> node(long)`, `Optional<CurveEdge> edge(long)`,
    `List<CurveEdge> edgesAt(long nodeId)`, `List<Obb> boxes(long edgeId)`,
    `List<SegmentBox> boxesNear(Aabb area)` (bounds'u `area` ile kesişen, tekrarsız),
    `Optional<RayHit> raycast(Vec3d origin, Vec3d dir, double maxDist)` (en yakın),
    `static Vec3d exitDirection(CurveEdge edge, long nodeId)` (düğümden çıkan ilk segment yönü, birim).

Dizin: segment kutusunun `bounds()`'unun kapladığı her blok hücresi (`floor`) için `long` anahtar
(`x`, `y`, `z` 21'er bit, işaretli) → `(edgeId, segment)` kümesi. Kutular `putEdge`'de `EdgeShape.boxes(shape, 0.5·diameter)`
ile bir kez hesaplanır.

- [ ] **Step 1: Failing GameTest'leri yaz**

```java
// graphIndexesAndForgetsEdges: düz kenar (0,0.5,0.5)->(4,0.5,0.5); boxesNear(Aabb(1,0,0,2,1,1)) bu kenarın
//   segmentlerini döndürür; removeEdge sonrası boş; edgesAt(from) kenarı içerir, sonra boş
// boxesNearSkipsFarEdges: ikinci kenar z=20'de -> Aabb(0,0,0,5,1,1) sorgusunda yok
// raycastPicksNearestEdge: iki paralel kenar y=1 ve y=3 (x boyunca); ışın (2,5,0.5) yön (0,-1,0)
//   -> edgeId y=3'teki, distance ≈ 5−3−0.1875 (1e-9); along ≈ segment içindeki x oranı (1e-9)
// removingConnectedNodeFails: kenarlı düğümü removeNode -> IllegalStateException; putEdge eksik düğümle -> IllegalArgumentException
// exitDirectionPointsAwayFromNode: dirsek kenarı -> exitDirection(e, from)·t0 > 1−1e-9; exitDirection(e, to)·(−tN) > 1−1e-9
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: Sınıfları yaz.**
- [ ] **Step 4: Doğrulama** (izin al): `All 113 required tests passed`.
- [ ] **Step 5: Commit** — `Index curved pipes for ray and volume queries`

### Görev 9: Yerleşim doğrulayıcısı, reach ve maliyet planı

**Files:**
- Create: `curve/BlockShapes.java`, `curve/FaceAttachment.java`, `curve/PlacementCandidate.java`,
  `curve/PlacementValidator.java`, `curve/Reach.java`, `curve/CostPlan.java`
- Modify: `gradle.properties` (`mod_version=0.3.9+1.20.1`, bu görevin derlemesi için)
- Test: `test/CurveRulesGameTests.java`

**Interfaces:**
- Consumes: `CurveGraph`, `JointRules`, `Obb`, `CurveRules`, `PlaceError`.
- Produces (Plan 3'ün sunucu işleyicisi bunları ek B6'daki sırayla çağırır: nesne (2) → boyut/reach (4) → şekil (1) →
  bu doğrulayıcı (5–8) → maliyet (3)):
  - `interface BlockShapes { List<Aabb> collision(int x, int y, int z); List<Aabb> outline(int x, int y, int z); }`
    — dünya koordinatlı kutular.
  - `record FaceAttachment(int x, int y, int z, int face)` — `face` Minecraft `Direction` sırası (0 DOWN … 5 EAST).
  - `record PlacementCandidate(List<CurveNode> nodes, List<CurveEdge> edges, Set<Long> replacedEdges, Map<Long, FaceAttachment> attachments)`
    — yeni ya da taşınan düğümler, yeni ya da değişen kenarlar, kaldırılan/değişen eski kenar kimlikleri, bloğa bağlı düğümler.
  - `static boolean PlacementValidator.onFace(Vec3d p, int face, List<Aabb> outline)` (ek B1 "blok yüzüne sabitlenen uç")
  - `static Optional<PlaceError> PlacementValidator.validate(CurveGraph graph, PlacementCandidate c, BlockShapes world)`
  - `static boolean Reach.tooFar(Vec3d eye, double blockReach, Vec3d p)` = `|p−eye|² > (blockReach + REACH_MARGIN)²`
  - `static int CostPlan.required(Collection<SolvedSpan> added, Collection<SolvedSpan> removed)` = `Σcost(added) − Σcost(removed)`
    (negatif = iade); `static boolean CostPlan.affordable(int required, int available, boolean creative)` =
    `creative || required ≤ available`.

`validate` sırası (ilk bulunan hata döner):

```text
1  her attachment: onFace(düğüm.pos, face, world.outline(x,y,z)) değilse INTERSECT_BLOCK
2  her yeni kenarın her kutusu: kapladığı hücrelerin world.collision kutularıyla overlaps → INTERSECT_BLOCK
3  her yeni kenarın her kutusu: graph.boxesNear(kutu.bounds()) içindeki, replacedEdges'de olmayan ve bu kenarla
   ortak düğümü olmayan kenarların kutularıyla overlaps → INTERSECT_PIPE; aday kenarlar birbirine karşı da (ortak düğüm hariç)
4  yeni kenara değen her düğüm: sonuç kenar kümesi (graf − replaced + yeni) ile
     R = jointRadius(çaplar, forceJoint); her kenarın karşı ucu nodeInJoint → NODE_IN_JOINT
     her kenar çifti için angleTooSmall(exitDirection…, r, r, R) → ANGLE_TOO_SMALL
```

`onFace`: `face` düzlemine indirgenmiş kutu (0 DOWN `y=minY`, 1 UP `y=maxY`, 2 NORTH `z=minZ`, 3 SOUTH `z=maxZ`,
4 WEST `x=minX`, 5 EAST `x=maxX`) her eksende `FACE_TOLERANCE` genişletilip `p`'yi kapsıyorsa true.
**Yaklaşık (ekten):** Uç bloğunu dar fazdan hariç tutmaya gerek yok çünkü değme kesişme sayılmaz (Görev 1).

- [ ] **Step 1: Failing GameTest'leri yaz** (test içinde `BlockShapes` lambda'larla: belirli hücrelerde tam küp)

```java
// onFaceRulesHold: birim küp outline; UP için (0.5,1.0099,0.5) true, (0.5,1.0101,0.5) false, (1.02,1,0.5) false;
//   altı yüzün her biri kendi düzlemindeki merkez noktasıyla true, karşı yüzle false
// reachAddsOneBlock: eye (0,0,0), reach 4.5: p (5.5,0,0) false (eşitlik geçer), (5.5001,0,0) true
// costPlanRefundsAndCharges: added cost 4 + 3, removed 5 -> 2; affordable(2,1,false) false; (2,1,true) true; (-3,0,false) true
// validateAcceptsFreeEdge: boş dünya, düz yeni kenar -> empty
// validateRejectsEdgeThroughBlock: (2,0,0) hücresinde tam küp; kenar (0,0.5,0.5)->(4,0.5,0.5) -> INTERSECT_BLOCK
// edgeRestingOnBlocksIsAllowed: x=0..3,y=0 hücrelerinde küp; kenar y=1+0.1875 boyunca -> empty
// validateRejectsCrossingPipes: grafta x boyunca kenar (y=0.5,z=2); aday z boyunca kenar x=2'den geçiyor -> INTERSECT_PIPE;
//   aynı aday, grafta kenarın uç düğümünden başlıyorsa (ortak düğüm, 90° ayrılıyor) -> empty
// validateRejectsSameDirectionBranch: grafta (0,0,0)->(4,0,0); aday (0,0,0) düğümünden (4,0.05,0)'a -> ANGLE_TOO_SMALL
// validateRejectsNodeInJoint: düğümde 2 kenar varken üçüncü kenar karşı ucu 0.2 uzakta (R=0.1875√2≈0.265) -> NODE_IN_JOINT
// validateRejectsFloatingAttachment: attachment UP, düğüm y=1.5 (yüzde değil) -> INTERSECT_BLOCK
```

- [ ] **Step 2: Derleme hatası beklenir.**
- [ ] **Step 3: Sınıfları yaz.**
- [ ] **Step 4: Doğrulama** (izin al): önce `gradle.properties` → `mod_version=0.3.9+1.20.1`.
  `.\gradlew.bat build --console=plain` → exit 0. `.\gradlew.bat runGameTestServer --console=plain` →
  `All 123 required tests passed`. `.\gradlew.bat runGameTestServer -PwithMekanism --console=plain` → aynı sayı.
- [ ] **Step 5: Commit** — `Validate curved placements`

---

## Plan 2 kapanışı

- Kovan `30-Hafiza/Projeler/Flowline.md`'ye durum yazılır (commit'ler, test sayısı, kararlar).
- Plan 3 (D: dünya verisi ve ağ) bu planın Interfaces bloklarından yazılır: `CurveGraph`'ı `SavedData`'ya sarma,
  `PipeNetwork` graf genellemesi, kıvrımlı uçta `SideConfig`, sunucu işleyicisi (B6 sırası), kirli blok kümesi
  (ek B4), maliyet tüketimi/iadesi, chunk senkronu.
