# Kendi kıvrımlı boru motoru — Plan 1/6: Curvy köprüsünü kaldırma (A) ve araştırma adımı (B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flowline'ı Curvy Pipes'a hiçbir bağımlılığı olmadan derlenir ve mevcut blok borusu davranışını koruyan hâle getirmek (A). Kendi motorun ihtiyaç duyduğu, raporlarda eksik kalan Curvy kurallarını statik analizle kesinleştirmek (B).

**Architecture:** A: `compat/CurvyPipesCompat` gibi Curvy köprüsü sınıfları silinir. Onlara dayanan iki yardımcı (`pipe(stack)`, item kaydı) `PipeBlock.isPipe(ItemStack)` ve düz `BlockItem` ile değiştirilir. Sol el modu `BUILD/CURVY` olarak kalır, ama motor gelene kadar `CURVY` seçilemez. B: Curvy 1.15.8 x64 görüntüsü üzerinde, mevcut RE araçlarıyla hedefli soruları cevaplayan bir ek rapor yazılır.

**Tech Stack:** Java 17, Forge 47.3.0 (MC 1.20.1), ForgeGradle 6, Forge GameTest; RE için Python 3.12 + capstone, Ghidra 12.1.4 (`CurvyAdditionForFlowLine/.analysis-tools`).

**Spec:** `docs/superpowers/specs/2026-10-18-kivrimli-boru-kendi-motor-design.md`

## Plan dizisi (yol haritası)

Tasarım birbirine bağlı altı parçaya bölünür. Her plan kendi başına derlenen ve test edilen bir sonuç üretir. C–F
planları, B'nin kesinleştirdiği değerlere dayandığı için **B bittikten sonra** yazılır.

| Plan | Kapsam | Bitince ne çalışır |
| --- | --- | --- |
| **1 (bu)** | A: Curvy köprüsünü kaldırma · B: araştırma eki | Curvy'siz derleme; blok boruları aynen çalışır; eksik Curvy kuralları yazılı |
| 2 | C: Saf Java geometri çekirdeği (`com.knozyy.flowline.curve`): kıvrım çözücü, yerleşim/çarpışma kuralları, joint, maliyet, ışın/hacim sorguları | Birim testleriyle doğrulanmış, Minecraft'tan bağımsız çekirdek |
| 3 | D: Dünya verisi ve ağ: kıvrımlı düğüm/kenar modeli, `SavedData`, `PipeNetwork` graf genellemesi, kıvrımlı uçta `SideConfig`/ekran, sunucuda yerleştir/düzenle/kır, maliyet tüketimi/iadesi, chunk senkronu | GameTest: tek Extract ile kıvrımlı hattan sandığa taşıma |
| 4 | E: İstemci: kare cam kanal mesh'i + cam düğüm render'ı, önizleme, HUD, onaylı tuş düzeni, 6 grid modu, seçenek çarkı | Oyunda döşeme/düzenleme (kullanıcı gözle doğrular) |
| 5 | F: Minecraft'a Mixin'ler: varlık çarpışması, seçme/kırma/orta tık, dünya değişimleri (B4'ün sonucuna göre) | Varlıklar borulara çarpar, borular seçilir/kırılır |
| 6 | G: Belgeler (README, `CURVED_PIPES_TR.md`, Patchouli), CHANGELOG, sürüm, son doğrulama | Teslim |

## Global Constraints

- Hedef: Minecraft 1.20.1, Forge 47.3.0, Java 17; dal `feature/own-curved-pipes` (taban `1.20.1`).
- Curvy Pipes'a derleme, çalışma ya da test bağımlılığı kalmaz.
- Curvy kodu kopyalanmaz, decompile çıktısı çevrilmez; yalnız davranış (formül, eşik, kural) kullanılır.
- Ayrı "kıvrımlı" item, tarif ya da creative girişi yok; mevcut `flowline:*_pipe` item'ları kullanılır.
- El düzeni: sağ el her zaman tek blok borusu; sol elde mod tuşu (varsayılan B) Build for Me ↔ Kıvrımlı.
- Her yeni derlemede `gradle.properties` `mod_version` artırılır.
- **Derleme, GameTest ve oyun başlatma kullanıcıya sorulmadan çalıştırılmaz.** Doğrulama adımları komutu ve
  beklenen çıktıyı verir; uygulayıcı çalıştırmadan önce izin ister.
- Commit kimliği `Knozyy <167627012+Knozyy@users.noreply.github.com>`, **atıf satırı yok**
  (`git -c user.name=Knozyy -c user.email=167627012+Knozyy@users.noreply.github.com commit ...`). Push yok.
- Kaynak dosyalar (lang, Patchouli, doku) elle değil `tools/gen_resources.py` ve `tools/gen_patchouli.py` ile
  üretilir. Betik ilgisiz dosyalara da dokunur (ör. GameTest yapılarının gzip zaman damgası). Bunlar
  `git checkout -- <dosya>` ile geri alınır; tüm checkout geri alınmaz.

## Review Focus

1. **Curvy'nin kurulu olduğu bir dünya/instance:** Curvy JAR'ı modlar klasöründe kalırsa Flowline çökmeden açılmalı;
   iki mod birbirinden habersiz çalışır. Test: Görev A2'nin elle kontrol adımı.
2. **Eski dünyada Curvy döneminin kalıntıları:** `curvy_pipes:flowline_*_pipe` envanter kayıtları ve
   `flowline_curves.dat`. Dünya açılmalı, eksik-kayıt uyarısı çıkmamalı (`AuditGameTests` mevcut remap testleri).
   Test: Görev A1'in `pipeItemsArePlainBlockItems` testi + mevcut `AuditGameTests`.
3. **Sol elde universal boru ve mod tuşu:** Eski kanal döngüsü kalkınca tuş Build for Me'de kalmalı ve açıklayıcı
   mesaj vermeli; istemci/sunucu protokol uyuşmazlığı bağlanmayı reddetmeli, çökmemeli.
   Test: Görev A3'ün `curvedModeIsUnavailableUntilTheEngineLands`.
4. **Ana elde boru, boruya sağ tık:** `PipeBlock.use` boru tutan oyuncuya uç ekranını açmamalı (bugünkü davranış).
   Test: Görev A1'in `pipeInHandDoesNotOpenSideScreen`.
5. **Build for Me, sol elde boru olmayan item:** "boru yok" mesajı (bugünkü davranış). Mevcut
   `PipeGameTests` Build for Me testleri kapsar; A1 sonrası yeşil kalmalı.

---

## A. Curvy köprüsünü kaldırma

### Görev A1: Curvy'siz boru item'ı ve `isPipe`

**Files:**
- Modify: `src/main/java/com/knozyy/flowline/pipe/PipeBlock.java` (`use` içindeki `CurvyPipesCompat.pipe` çağrısı, ~283)
- Modify: `src/main/java/com/knozyy/flowline/registry/ModItems.java:43-45` (`blockItem`)
- Modify: `src/main/java/com/knozyy/flowline/pipe/PipeBuilder.java:48`
- Modify: `src/main/java/com/knozyy/flowline/client/BuildHint.java:21-22`
- Modify: `src/main/java/com/knozyy/flowline/client/ClientEvents.java:34`
- Test: `src/main/java/com/knozyy/flowline/test/PipeGameTests.java`

**Interfaces:**
- Produces: `public static boolean PipeBlock.isPipe(ItemStack stack)`: item bir `BlockItem` ve bloğu `PipeBlock` ise
  `true`. Sonraki bütün görevler "elde boru var mı" için bunu kullanır.

- [ ] **Step 0: Başlangıç sayısını teyit et** (izin al): Değişiklikten önce `.\gradlew.bat runGameTestServer --console=plain`
  çalıştırılır. Plan dosyalardaki `@GameTest` sayımından 70 bekliyor; farklıysa aşağıdaki bütün sayılar o farka göre
  düzeltilir.

- [ ] **Step 1: Failing GameTest'leri yaz** (`PipeGameTests`, mevcut `@GameTest` desenine uyarak)

```java
// pipeItemsArePlainBlockItems: her PipeType için
//   ModItems.<tür>_PIPE.get() instanceof BlockItem bi && bi.getBlock() instanceof PipeBlock
//   PipeBlock.isPipe(new ItemStack(<tür>_PIPE)) == true
//   PipeBlock.isPipe(new ItemStack(Items.STONE)) == false
//   PipeBlock.isPipe(ItemStack.EMPTY) == false
// pipeInHandDoesNotOpenSideScreen: sandığa bağlı boruya, ana elde item_pipe tutan sahte oyuncuyla use →
//   InteractionResult.PASS ve açık menü yok (player.containerMenu == player.inventoryMenu)
```

- [ ] **Step 2: Derleme hatası beklenir** (`isPipe` yok). Testler bir sonraki adımda derlenir.

- [ ] **Step 3: `PipeBlock.isPipe(ItemStack)` ekle; çağrı yerlerini değiştir.** `ModItems.blockItem` →
  `new BlockItem(block.get(), new Item.Properties())`. `PipeBlock.use`, `PipeBuilder.build`, `BuildHint`'teki
  `CurvyPipesCompat.pipe(...)` → `PipeBlock.isPipe(...)`. `ClientEvents` tooltip koşulu
  `CurvyPipesCompat.supported(stack)` → `PipeBlock.isPipe(stack)`.

- [ ] **Step 4: Doğrulama** (izin al): `.\gradlew.bat runGameTestServer --console=plain` → logda
  `All 72 required tests passed` (70 + bu görevin 2 testi; `-PwithCurvy` olmadan Curvy testleri zaten koşmaz).
  Bu bir GameTest koşusudur, dağıtım derlemesi değil; sürüm A3'te artırılır.

- [ ] **Step 5: Commit** — `Recognise pipes without Curvy Pipes`

### Görev A2: Curvy bağımlılığını ve köprü sınıflarını silme

**Files:**
- Delete: `src/main/java/com/knozyy/flowline/compat/CurvyPipesCompat.java`, `compat/CurvyPort.java`,
  `compat/UniversalChannel.java`, `compat/PipePlacement.java`, `mixin/CurvyClientMixin.java`,
  `mixin/CurvyConfigMixin.java`, `mixin/CurvyInventoryMixin.java`, `mixin/CurvyPipeItemMixin.java`,
  `test/CurvyIntegrationGameTests.java`
- Modify: `src/main/java/com/knozyy/flowline/test/GuiGameTests.java` (`curvyPortBridgesTwoNetworks` testi silinir)
- Modify: `src/main/resources/flowline.mixins.json` → `"mixins": []`, `"client": []` (dosya, Plan 5'in Mixin'leri
  için kalır; `MixinConfigs` manifest girdisi korunur)
- Modify: `src/main/resources/META-INF/mods.toml` (`curvy_pipes` bağımlılık bloğu silinir)
- Modify: `build.gradle` (`withCurvy` koşullu `runtimeOnly curse.maven:curvy-pipes...` bloğu;
  `gametest-curvy` çalışma dizini dalı; `snakeyaml-engine` `minecraftLibrary` ve `jarJar` satırları). `jarJar.enable()`
  ve reobf ayarları kalır.
- Keep: `compat/LegacyCurveNotice.java` (spec gereği; yalnız mesaj metni A4'te değişir)

**Interfaces:**
- Consumes: `PipeBlock.isPipe(ItemStack)` (A1)

- [ ] **Step 1: Silmeden önce referansları kontrol et:** `grep -rn "CurvyPipesCompat\|CurvyPort\|UniversalChannel\|PipePlacement\|org.snakeyaml" src/main/java`
  yalnız silinecek dosyaları ve A3'ün ele alacağı `OffhandPipe`/`OffhandMode`/`ModNetwork`'ü göstermeli.
- [ ] **Step 2: Dosyaları sil, yukarıdaki düzenlemeleri yap.**
- [ ] **Step 3: Doğrulama** (izin al; A3 bitmeden derleme kırık olabilir, A2 ile A3 tek doğrulamada birleşir).
  Doğrulama Görev A3 Step 4'te yapılır.
- [ ] **Step 4: Commit** — A3 ile birlikte (aşağıda).

### Görev A3: Sol el modu ve ağ paketi

**Files:**
- Modify: `src/main/java/com/knozyy/flowline/pipe/OffhandMode.java` (`CHANNEL`, `channelOf`, `setChannel` silinir)
- Modify: `src/main/java/com/knozyy/flowline/network/OffhandModePayload.java` → `record OffhandModePayload(OffhandMode mode)`
- Modify: `src/main/java/com/knozyy/flowline/network/ModNetwork.java` (`PROTOCOL` `"10"` → `"11"`; `setChannel` çağrısı silinir)
- Modify: `src/main/java/com/knozyy/flowline/client/OffhandPipe.java` (kanal alanı/`universal`/`channel()` silinir)
- Test: `src/main/java/com/knozyy/flowline/test/PipeGameTests.java`

**Interfaces:**
- Produces: `OffhandPipe.curvedAvailable()` → `static boolean`, şimdilik `false`. Plan 4 motor gelince `true` yapar.
  `OffhandPipe.mode()` = `curvedAvailable() ? mode : OffhandMode.BUILD`. Mod tuşu `curvedAvailable()` false iken
  modu değiştirmez ve `message.flowline.offhand.curved_soon` gösterir. Sunucu `OffhandMode.of(player)` aynen kalır.

- [ ] **Step 1: Failing GameTest** `curvedModeIsUnavailableUntilTheEngineLands`:
  `OffhandMode.of(fakePlayer) == BUILD`. `ModNetwork` üzerinden `new OffhandModePayload(CURVY)` işlenince bile
  `PipeBuilder` Build for Me davranışı değişmez (mevcut bir Build for Me test kurulumunu yeniden kullan).
  `OffhandModePayload` encode→decode aynı `mode`'u verir.
- [ ] **Step 2: Kodu değiştir.**
- [ ] **Step 3: Kalan referans taraması:** `grep -rn -i "curvy" src/main/java` → yalnız `LegacyCurveNotice` ve
  `hud.flowline.mode.curvy` gibi lang anahtar adları kalmalı.
- [ ] **Step 4: Doğrulama** (izin al): Önce `gradle.properties` → `mod_version=0.3.7+1.20.1`.
  `.\gradlew.bat build --console=plain` → `BUILD SUCCESSFUL`.
  `.\gradlew.bat runGameTestServer --console=plain` → `All 72 required tests passed`. Hesap: başlangıçta 70
  (Audit 16 + Gui 9 + Pipe 45; Curvy ve Mekanism testleri yalnız `-PwithCurvy`/`-PwithMekanism` ile koşar), eksi
  `curvyPortBridgesTwoNetworks`, artı A1/A3'ün 3 testi. Log `run/gametest-normal` altında. Exit 0 tek başına
  yeterli değil, satır aranır. `.\gradlew.bat runGameTestServer -PwithMekanism --console=plain` de yeşil kalmalı.
- [ ] **Step 5: Elle kontrol (kullanıcı):** Instance'ta Curvy JAR'ı varken Flowline açılır mı (Review Focus 1)?
  Kullanıcıya sorulur; yapılmazsa "doğrulanmadı" diye kaydedilir.
- [ ] **Step 6: Commit** (A2+A3) — `Remove the Curvy Pipes bridge`

### Görev A4: Metinler, rehber, sürüm

**Files:**
- Modify: `tools/gen/lang.py` (Curvy'den söz eden anahtarlar, aşağıda); `tools/gen_patchouli.py` (`curved` kategorisi
  ve `curvy` girdisi)
- Generated: `src/main/resources/assets/flowline/lang/{en_us,tr_tr}.json`, `src/main/resources/data/flowline/patchouli_books/guide/*`
- Modify: `CHANGELOG.md` (`## Unreleased` bölümü), `gradle.properties` (`mod_version=0.3.8+1.20.1`, bu görevin derlemesi için)

Metin kararları (EN / TR):
- `key.flowline.build`: "Off-hand pipe mode (Build for me / Curved)" / "Sol el boru modu (Build for Me / Kıvrımlı)"
- `tooltip.flowline.curvy.hands`: "Main hand: block pipe | Off hand: Build for me (curved placement is coming back)" / "Sağ el: blok boru | Sol el: Build for Me (kıvrımlı yerleşim geri geliyor)"
- `message.flowline.offhand.curved_soon` (yeni, `no_curvy` yerine): "Curved placement is being rebuilt into Flowline itself" / "Kıvrımlı yerleşim Flowline'ın kendi özelliği olarak yeniden yapılıyor"
- `hud.flowline.build.ready`: "· [%s] switches to Curvy" kısmı kaldırılır
- `hud.flowline.mode.curvy` → "Curved" / "Kıvrımlı"; `hud.flowline.mode.curvy_channel` ve
  `guide.flowline.universal_pipe.2` silinir
- `message.flowline.curvy.legacy`: "…then rebuild them with Flowline's curved placement." / "…sonra Flowline'ın
  kıvrımlı yerleşimiyle yeniden kur."
- Patchouli `curvy` girdisi silinir; `curved` kategorisinde yalnız Build for Me kalır (Plan 6 kıvrımlı girdiyi yazar)

- [ ] **Step 1:** Metinleri değiştir; `python tools/gen_resources.py` ve `python tools/gen_patchouli.py` çalıştır.
- [ ] **Step 2:** `git status` → ilgisiz değişen dosyaları `git checkout --` ile geri al.
- [ ] **Step 3:** `grep -rn -i "curvy pipes" src/main/resources` yalnız eski dünya mesajı dışında boş olmalı.
- [ ] **Step 4: Doğrulama** (izin al): A3 Step 4'ün iki komutu; `AuditGameTests` lang/rehber denetimleri dahil yeşil.
- [ ] **Step 5: Commit** — `Drop Curvy Pipes from texts and the guide`

---

## B. Araştırma adımı (Curvy 1.15.8, statik)

**Çalışma yeri:** `C:\Users\kanad\Desktop\Projeler\mod editörü\CurvyAdditionForFlowLine`. Mevcut araçlar:
`docs/evidence/analyze_curvy_blob.py` (`--dump`, `--data`, `--find`, `--all-refs`),
`docs/evidence/run_curvy_ghidra.ps1 -Resume -OutputName <ad>.c -Addresses @(...)` (**ASCII dizinden** çalıştır:
`Set-Location $env:TEMP\CurvyNativeAnalysis`), `docs/evidence/arm64_crossmap.py --verify-constants`,
`docs/evidence/jni_table.py` (tablo etiketleri `native/jni-table.json`).

**Çıktı:** `docs/curvy-behaviour-addendum-re.md`. Her soru için:
- Cevap
- Adresli kanıt (fonksiyon + komut aralığı)
- Sayısal sabitlerin görüntüden okunmuş değerleri
- Plan 2–5'in kullanacağı **Java'ya dönük kural cümlesi**

Çözülemeyen bir nokta "yaklaşık" diye işaretlenir ve gerekçesi yazılır.

**Doğrulama ölçütü (her B görevi):** Ek rapordaki ilgili bölüm aşağıdaki "bitti sayılır" maddelerinin hepsini
cevaplar. Sayısal her sabit ARM64'te de aranır (`--verify-constants` ya da `findconst` yöntemi).

### Görev B1: Yerleşim hatalarının tetikleyicileri

- [ ] `too_far` (4): sınır değeri ve neyin ölçüldüğü (oyuncu menzili mi, kenar uzunluğu mu, düğüm arası mı). Başlangıç:
  sunucu mesaj switch'i `0x101caa60` ve hata kodu 4'ü yazan yerler (`curvy-geometry-native-re.md` hata tablosu).
- [ ] `invalid_shape` (1) ve `invalid_object` (2): hangi koşullar.
- **Bitti sayılır:** Üç hatanın her biri için "şu durumda, şu eşikle" cümlesi ve ondalıklı eşik değerleri.

### Görev B2: Kıvrım çözücünün tam tanımı

- [ ] Hangi düğüm yönleri sabit (blok yüzü, boru yüzü, serbest uç, uzatma, joint); açıklıklar arası süreklilik.
- [ ] Amaç fonksiyonunun terimleri ve ağırlıkları (`0x10130530`), kısıtlar, iterasyon sınırı ve yakınsama
  (`0x10133390`'ın `−0.5`, `1e-8`, `0.01` literal'lerinin rolü), başarısızlıkta hangi hata.
- **Bitti sayılır:** Plan 2'nin uygulayıcısının çözücüyü raporu okuyarak yazabileceği bir matematiksel tanım ve en az
  iki örnek giriş için beklenen davranış (segment sayısı `n`, `h`, ret/kabul).

### Görev B3: Dar faz çarpışma testleri

- [ ] `0x100f1c10` (boru–boru) ve `0x10011e70` (boru–blok): borunun hangi geometriyle temsil edildiği (kapsül,
  segment kutuları, `steps` AABB listesi), mesafe/eşik değerleri.
- **Bitti sayılır:** Her iki test için şekil + eşik + "kesişiyor" koşulu. Spec, Flowline'da kare kesitin kullanılacağını
  söylediğinden ek rapor yalnız Curvy'nin yaptığını yazar; uyarlamayı Plan 2 yapar.

### Görev B4: Dünya değişimlerinde davranış

- [ ] `LevelChunk.setBlockState` yaması (`1022c240`) + hook kayıt 8 (`0x101d5cc0`, `physics.rs:21`): blok değişince
  ne olur (önbellek geçersizleştirme mi, kenar kırma mı, yerleşimi reddetme mi).
- [ ] Bir kıvrımın geçtiği yere blok konabilir mi; patlama/piston/düşen blok (`FallingBlockEntity`, hook kayıt 5)
  etkisi.
- [ ] Varlık çarpışması (`EntityGetter`/`CollisionGetter` yamaları, hook 3 ve 5): hangi varlıklar, hangi şekil.
- **Bitti sayılır:** Her olay için "Curvy şunu yapar" cümlesi; Plan 5'in Mixin listesi bu bölümden çıkarılabilir.

### Görev B5: Uç ayrılması ve sabitleme

- [ ] Bağlı blok kırılınca ya da uç taşınınca davranış ("Detaching from block – configurations will be lost"
  metnine giden yol); envanter olmayan blok yüzüne sabitlemenin koşulları.
- **Bitti sayılır:** Ayrılma/sabitleme durum geçişleri (bağlı → serbest, serbest → bağlı) ve her geçişte ayarların akıbeti.

### Görev B6: Grid modları ve düzenleme işlemleri

- [ ] `curvy-protocol-editor-re.md`'deki altı modun her biri için: aday kümesi, hizalama adımı, ekran projeksiyonu
  seçim kuralı. Düzenleme işlemlerinin (yerleştir, düğüm taşı, ayır, birleştir, force_joint, sil, uzat, dallandır)
  ön koşulları ve maliyet farkı.
- **Bitti sayılır:** Plan 4'ün HUD/editör'ü için her mod ve işlem için tek paragraflık davranış tanımı.

- [ ] **B kapanışı:** Ek raporu `docs/curvy-re-status.md`'ye bağla. Kullanıcıya özeti sun ve **Plan 2'yi yaz**. RE
  deposunda commit yalnız kullanıcı isterse yapılır.
