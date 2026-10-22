# Kendi kıvrımlı boru motoru — Plan 5/6: Minecraft'a dokunulan yerler (Mixin)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Kıvrımlı borular için gereken oyun kancaları (ek B4 "Java kuralı"): dünya değişiminin
kıvrımı kırması her blok değişiminde (komşusuna haber vermeyenler dahil), varlıkların kıvrımlı borulara çarpması ve
üstünde durması (düşen bloklar dahil), istemcide havaya orta tık. Seçme ve sol tık Plan 4'te Forge olaylarıyla
çözüldü; Mixin gerekmez.

**Architecture:** Mixin'ler ince kalır, işi `curved`/`client.curve` sınıflarına devreder. Saf kısım
(`curve.CurveVoxels`: yönlü kutuyu eksen hizalı dilimlere bölmek) GameTest'le sınanır.

**Spec:** `docs/superpowers/specs/2026-10-18-kivrimli-boru-kendi-motor-design.md` ("Referans davranış" son satır,
"Kare kesit ve çarpışma"). **Davranış:** ek B4.

## Global Constraints

- Java 17, Forge 47.3.0, Mojmap; Mixin 0.8.5 + MixinGradle 0.7.38 (refmap `flowline.refmap.json`). Vanilla hedefleri
  refmap'le SRG'ye çevrilir; refmap jar'da olmalı.
- Hedefler (Mojmap, 1.20.1): `LevelChunk.setBlockState(BlockPos, BlockState, boolean)`, `Entity.collide(Vec3)` içindeki
  `Level.getEntityCollisions(Entity, AABB)` çağrısı, istemci `Minecraft.pickBlock()`.
- Başlangıç **186** test. Commit Knozyy, atıf yok, push yok. Her derlemede sürüm artar (0.3.13).

## Review Focus

1. **Komşuya haber vermeyen değişim** (bayrak 2, piston ara durumu): kıvrım yine kırılmalı. Test
   `silentBlockChangeBreaksCurves`.
2. **Varlık çarpışması kare kesitle:** dilimler kutuyu kapsamalı; dünyaya hizalı kesitte yana taşma yok, eğik kesitte
   en çok 2 px (uygulamada ölçüldü; 1 px için segment başına yüzlerce kutu gerekir). Test
   `obbVoxelsCoverTheBox`; dünyada `itemsLandOnCurvedPipes`.
3. **Maliyet:** her varlık hareketinde çağrılır; kıvrım yoksa erken çıkış.

### Görev 1: Mixin derleme zinciri

**Files:** Modify `settings.gradle` (Sponge eklenti deposu), `build.gradle` (`org.spongepowered.mixin` 0.7.38, `mixin {}`,
`annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`), `flowline.mixins.json` (`refmap`).

- [ ] `.\gradlew.bat compileJava` exit 0. Commit `Build mixins with a refmap`.

### Görev 2: Her blok değişimi kirli işaretlenir

**Files:** Create `mixin/LevelChunkMixin.java` (`setBlockState` RETURN, eski durum null değilse ve seviye sunucuysa
`CurveData.blockChanged`), Modify `curved/CurveData.java` (`static blockChanged(Level, BlockPos)`: kıvrım yoksa hemen
çıkar), Delete `curved/CurveWorldEvents.java`; Test `test/CurveWorldGameTests.java`.

- [ ] Failing test `silentBlockChangeBreaksCurves`: kıvrımın geçtiği hücreye `setBlock(pos, stone, 2)` → sonraki tick
  kenar yok, borular düşer. Step: `All 187 required tests passed`. Commit `Notice every block change under curved pipes`.

### Görev 3: Varlıklar kıvrımlı borulara çarpar

**Files:** Create `curve/CurveVoxels.java` (saf: `List<Aabb> boxes(Obb box, double slice)`), `curved/CurveCollisions.java`
(`List<VoxelShape> shapes(Level, AABB)`: sunucuda `CurveData`, istemcide `ClientCurves`), `mixin/EntityMixin.java`
(`collide` içindeki `getEntityCollisions` sonucuna ekler); Test `test/CurveGeometryGameTests.java`,
`test/CurveWorldGameTests.java`.

- [ ] Failing testler `obbVoxelsCoverTheBox` (eğik kutu: kutunun içindeki örnek noktalar dilimlerde; dilim köşeleri
  kutunun 2 px şişirilmişinde), `itemsLandOnCurvedPipes` (havadaki yatay kenarın üstüne bırakılan eşya kenarın üst
  yüzünde durur). Step: `All 189 required tests passed`. Commit `Let entities stand on curved pipes`.
- **Yaklaşık:** VoxelShape eksen hizalıdır; eğik segment ince dilimlerin AABB'leriyle kaplanır (en çok 2 px taşma).
  Hariç tutulan varlık türü yok (ek B4); hepsi çarpar.

### Görev 4: Havada orta tık

**Files:** Create `mixin/client/MinecraftMixin.java` (`pickBlock` HEAD → `CurveInput.pickBlock()` true ise iptal),
Modify `client/curve/CurveInput.java` (PICK yalnız buradan), `flowline.mixins.json` (`client`).

- [ ] `compileJava` temiz; davranış istemcide gözle (izin yoksa "doğrulanmadı"). Commit `Turn the grid with the middle
  button in the air`.

### Görev 5: Derleme ve kayıt

- [ ] `.\gradlew.bat build` (sürüm `0.3.13+1.20.1`, jar'da `flowline.refmap.json`), normal ve `-PwithMekanism`
  GameTest aynı sayı. Kovan güncellenir.
