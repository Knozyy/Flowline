# Flowline: kendi kıvrımlı boru motoru — tasarım

Tarih: 2026-10-18 · Dal: `feature/own-curved-pipes` (taban `1.20.1`) · Hedef: Minecraft 1.20.1, Forge 47.3.0, Java 17

## Niyet

Flowline'ın önceki kıvrımlı hatları dışarıdan bir köprüyle çalışıyordu ve Flowline ağından **ayrı bir taşıma
sistemi** idi. Sandığa bağlı Flowline borusuna kıvrımlı bir hat takıldığında iki ayrı sistem oluşuyor ve ikisinin de
ayrı ayrı Extract'a alınması gerekiyordu. Hattın ayar ekranı da ancak hat bir yere bağlıyken açılabiliyordu.
Kullanıcı bu "ayrı extract/insert sistemi"nden kurtulmak istiyor.

Amaç: Kıvrımlı yerleşimi **Flowline borusunun kendi özelliği** yapmak. Kıvrımlı bir boru, aynı ağın parçası olan ve
yalnız yerleşimi ile görünümü farklı bir Flowline borusu olur. Sandığa bağlı boruyu bir kez Extract yapmak, bağlı
kıvrımlı hat dahil bütün ağı çalıştırır. Başka bir moda bağımlılık kalmaz.

Fizik ve geometri kuralları önceden belirlenmiş sayılar ve formüllerdir; motor bunları Java'da sıfırdan yazar.

## Hedefler

1. **Kendi motor:** Kıvrımlı borular başka bir mod olmadan çalışır. Kıvrım, yerleşim, çarpışma, seçme/kırma,
   varlık çarpışması, maliyet ve render Flowline kodudur.
2. **Tek ağ:** Kıvrımlı boru, aynı türdeki blok borularıyla ve diğer kıvrımlı borularla aynı `PipeNetwork` grafının
   parçasıdır. Rota, mesafe, dağıtım, öncelik, hız, upgrade'ler ve "keep"/limit davranışı Flowline'ındır.
3. **Uç ayarları tek sistem:**
   - Kıvrımlı borunun ucu doğrudan bir envantere (sandık, tank, makine yüzü) bağlanabilir. O uç, blok borusunun bir
     yüzüyle **aynı** `SideConfig`, ekran, wrench davranışı, Filter Card ve upgrade'leri kullanır.
   - Ucu bir boruya bağlıysa ayarı yoktur, yalnız taşır.
4. **Tutarlı fizik:** Yerleşim kuralları, kıvrım biçimi, joint, çarpışma ve maliyet aşağıdaki "Referans davranış"
   bölümündeki kurallara uyar.
5. **Görünüm:** Kıvrımlı boru, blok borusunun **kare "cam kanal + köşe rayları"** tasarımını eğri boyunca taşır.
   - Kare kesit, yarı saydam cam gövde.
   - Dört köşede tür renginde ray.
   - Aralıklı çerçeve/bilezik.
   - Boya blok borusundaki gibi rayları/kafesi boyar.

   Blok borusuna bağlandığı yerde geçiş fark edilmemeli.

   Ayrıntılar:
   - **Havadaki birleşme noktası (joint/dal):** Kullanıcının seçimi **yumuşak cam düğüm**. Kanallar camdan yuvarlak
     bir düğümde birleşir, her kanal girişinde ince metal bilezik vardır. Düğümün ölçüsü joint yarıçapı `R`'dir.
   - **Envantere bağlı uç:** Blok borusundaki uç halkası/kapak gibi görünür. Extract uçlarında blok borusundaki
     yeşil halka (`Conn.EXTRACT`) aynen kullanılır. *(Tasarımcının kararı.)*
6. **Bütün boru türleri:** Eşya, sıvı, enerji, universal ve Mekanism chemical boruları kıvrımlı döşenebilir.
   Universal borunun eski "kanal" hilesine (`UniversalChannel`, kanal döngüsü) gerek kalmaz.
7. **Tam döşeme/düzenleme araçları:** Altı grid/hedefleme modu, önizleme, hata mesajları, düğüm taşıma,
   hattı ayırma/birleştirme, `force_joint`, silme ve maliyet gösterimi.

## Hedef olmayanlar (non-goals)

Bunlar yapılmayacak, çünkü yerlerini Flowline'ın kendi sistemleri tutuyor:

- Ayrı bir taşıma motoru: widest-path rota, kenar hız borcu, ayrı tick zamanlayıcısı.
- Kıvrımlı hatlara ayrı filtre ekranı, regex filtresi ve regulator. Flowline'da miktar sınırı zaten var.
- Kızıltaş kablosu boru türü ve kızıltaş yamaları.
- AE2, GregTech, ComputerCraft ve Storage Drawers kablo entegrasyonları.
- YAML config, 15 boru tipi/5 boyut ve dönüşüm zinciri tarifleri.
- Başka modların kıvrımlı hatlarından veri aktarımı. Kullanıcı modu henüz bir pakete koymadı; eski hatların
  kaybolması sorun değil.
- 1.21.1 (NeoForge) dalına taşıma. Sonraki iş.

## Anti-hedefler (çalışsa bile başarısızlık sayılır)

- Kıvrımlı hat için ayrı bir Extract/Insert adımı ya da ayrı bir ayar ekranı gerekmesi.
- Başka bir moda derleme, çalışma ya da test bağımlılığı kalması.
- Yuvarlak/sekizgen kesit. Kullanıcı blok borularında açıkça kare istedi ve kıvrımlıda da kareyi seçti.
- Blok borusundan kıvrımlıya geçişte biçim veya renk kırılması.
- Mevcut blok borusu davranışının (yerleşim, Build for Me, GUI, filtreler, upgrade'ler, GameTest'ler) bozulması.

## Kısıtlar

- **Lisans:** Flowline MIT. Uygulama sıfırdan yazılır.
- **El düzeni (Kovan'daki kalıcı karar):**
  - Sağ (ana) el her zaman tek blok borusu yerleştirir.
  - Sol elde mod tuşu (varsayılan B, Controls'tan değiştirilebilir) **Build for Me ↔ Kıvrımlı** arasında geçer.
    Seçili mod ekranda yazar.
  - Sağ tık seçili modun işini yapar.
  - `OffhandMode.CURVED` artık kendi motoru açar. Universal için kanal adımı kalkar; mod döngüsü yine iki adımlıdır.
  - **Kıvrımlı moddaki tuşlar** *(tasarımcının önerisi, kullanıcı onayladı; hepsi Controls'tan değiştirilebilir)*:

    | Tuş | İş |
    | --- | --- |
    | Sağ tık | Hattı başlat, sonraki noktayı koy, dallandır, bitir |
    | Sol tık | Bakılan kıvrımlı kenarı kır; survival'da anında kırılır, maliyet iade edilir |
    | Sol Alt basılı | Seçenek çarkı: Sil, Dallandır, Uzat, Taşı, Force Joint, Ayarlar |
    | Orta tık / Shift+orta tık | Grid modu ileri / geri |
    | Ctrl basılı | Blok/boru/düğüme yapışmayı geçici kapat |
    | Esc ya da ikinci B | Yarım hattı iptal et |
- **Aynı item:** Ayrı bir "kıvrımlı boru" item'ı, tarifi ya da creative girişi yok. Mevcut `flowline:*_pipe`
  item'ları iki yerleşimi de yapar. Maliyet oyuncunun aynı stack'lerinden düşer; kırılınca iade edilir. Survival'da
  eksik malzeme `too_costly` hatası verir.
- **Koruma:** Yerleşim ve kırma, Forge koruma olaylarına (claim modları) blok borusundaki gibi saygı gösterir.
- **Sunucu otoritesi:** Yerleşim ve düzenleme isteklerini sunucu doğrular. İstemci yalnız önizler.
- **Sürüm kuralı:** Her yeni derlemede `gradle.properties` içindeki `mod_version` artırılır. Bu bütün mod projelerinde
  geçerli bir kullanıcı kuralıdır.
- **Komutlar:** Derleme, GameTest ve oyun başlatma kullanıcıya sorulmadan çalıştırılmaz. Commit/push yalnız kullanıcı
  isteyince yapılır.

## Referans davranış

Motorun uyduğu kurallar:

| Konu | Bu motor için özet |
| --- | --- |
| Kıvrım çözücü | Her açıklıkta örnek aralığı `s` = boru çapı. `n = ceil(clamp(A/s, 1, 64))`. `d < 0.5·s` ise ret. Eşit uzunlukta segmentler ve yön değişimini cezalandıran amaç, seyrek doğrusal olmayan çözümle. Çözücü sabitleri: sönüm `0.5`, düzenlileştirme `1e-8`, durma eşiği `0.01`. Uç yönü sabitse uç komşuları `P0 + h·t0` / `P1 − h·t1`. |
| Yerleşim hataları | `invalid_shape` (1), `invalid_object` (2), `too_costly` (3), `too_far` (4), `intersect_block` (5), `intersect_pipe` (6), `angle_too_small` (7), `node_in_joint` (8). Kullanıcıya yerelleştirilmiş mesaj gösterilir. |
| Çarpışma | Blok: bölgedeki blokların collision kutularıyla dar faz (5). Boru: önce AABB, ortak düğümlü komşu kenarlar hariç, sonra dar faz (6). `‖uç − düğüm‖² < R²` → 8. Aynı düğümdeki çıkış yönleri: `dot ≥ 0.999` → 7. `0.1 < dot < 0.999` aralığında kalınlık boşluğu formülü → 7. |
| Yarıçaplar | Boru yarıçapı `r = 0.5·çap`. Joint yarıçapı `R = 0.5·max(bağlı kenar çapları)`. ≥3 kenar ya da `force_joint` ise `R ·= √2`. Uç/geçiş bayrağı: tek kenarlı düğüm ya da farklı çaplı iki kenar. |
| Maliyet | Kenar başına `cost = ceil(n·h)` boru item'ı (yukarı yuvarlanmış uzunluk). Düzenlemede yeni − eski fark ödenir/iade edilir. |
| Fizik sorguları | Işın sorgusu (bakılan boruyu seçme, kırma, orta tık) ve hacim/çakışma sorgusu (varlık çarpışması). Zaman adımlı simülasyon yok; statik geometri. |
| Render | Kesit tabloları, frame taşıma, normal/UV, uç kapakları, kamera göreli konum, kenar başına mesh önbelleği. **Kesit burada kare cam kanaldır.** |
| Grid/editör | Altı mod, aday kümeleri, 27 hücrelik outline ile eleme, ekran projeksiyonunda seçim, graph düzenleme işlemleri (yerleştir, düğüm taşı, ayır, birleştir, `force_joint`, sil). |
| Minecraft'a dokunulan yerler | Varlık çarpışması, seçme ve kırma için **Mixin** ile şu sınıflara bağlanılır: `Entity`, `EntityGetter`, `CollisionGetter`/`Level`, `FallingBlockEntity`, `LevelChunk.setBlockState`, `Minecraft` (`continueAttack`, `pickBlock`). Kızıltaş, dil/kaynak ve AE/GT yamaları kapsam dışı. |

**Boyut:** Kıvrımlı kesit, blok borusunun 6/16 bloklu kanalıyla aynıdır. Çözücüye giden "çap" `6/16 = 0.375` olur.
*(Tasarımcının kararı.)* Daha ince bir çap (0.2) Flowline'ın görünümüne uymuyor.

**Kare kesit ve çarpışma** *(tasarımcının kararı)*:
- Joint/açı formülleri yuvarlak boru varsayar. Görünen kare köşelerin hiçbir şeye girmemesi için **blok/boru kesişme
  testi (5, 6), varlık çarpışması ve ışınla seçme gerçek kare kesiti** (segment başına yönlü kutu) kullanır.
- Açı ve joint kuralları (7, 8) ve joint yarıçapı yuvarlak boru formülünü korur. Formüldeki `r` ve çap değerleri
  `0.5·0.375` ve `0.375`'tir.

### Araştırma adımı (uygulamadan önce)

*(Tasarımcının kararı.)* Kullanıcı tahmine dayalı motoru daha önce reddetti. Bu yüzden raporlarda tanımlı olmayan
kurallar **tahmin edilmez**. Plan, çözücü ve yerleşim işinden önce hedefli bir statik RE adımı içerir. Bulguları
raporlara eklenir:

1. `too_far` (4), `invalid_shape` (1) ve `invalid_object` (2) hatalarını tetikleyen koşullar.
2. Hangi düğüm yönlerinin sabit olduğu: uzatılan serbest uç, açıklıklar arasında süreklilik, joint'ler.
3. Dar faz testlerinin biçimi.
4. Dünya değişiminde davranış: `LevelChunk.setBlockState`, `FallingBlockEntity`, patlama ve piston yamalarının yaptığı iş.
5. Ucun bağlı olduğu blok kırılınca ne olduğu.

Bir kural yine de çözülemezse plan bunu açıkça "yaklaşık" diye işaretler ve kullanıcıya bildirir.

**Dünya değişimleri ve bağlantı noktaları** *(tasarımcının kararı; ayrıntı araştırma adımında kesinleşir)*:
- Kıvrımlı uç, envanter olmayan herhangi bir katı blok yüzüne de **sabitlenebilir**. Böyle bir uç ağ ucu
  değildir ve `SideConfig` taşımaz.
- Bir ucun bağlı olduğu blok kırılır ya da uç oradan taşınırsa uç serbest düğüme döner.
  - O uçtaki `SideConfig` silinir.
  - Takılı upgrade'ler ve Filter Card uç konumunda eşya olarak düşer; kaybolmaz.
- Oyuncular ve canlılar kıvrımlı boruların üstünde durabilir ve onlara çarpar. Bir kıvrımın geçtiği yere blok koyma
  ve diğer dünya etkileşimleri araştırma adımında belirlenen davranışı izler.

## Ağ bütünleşmesi (gereksinimler)

`PipeNetwork` bugün yalnız `BlockPos` düğümlerinden ve `Direction` yüzlerinden oluşan bir taşma grafı kuruyor.
Kıvrımlı borular için gerekenler:

- **Kıvrımlı düğümler:** Bir kıvrımlı hattın düğümleri dört yerde durabilir:
  - Blok borusunun bir yüzünde: o yüz ağ bağlantısı olur ve `Conn.PIPE` gibi davranır.
  - Bir envanterin yüzünde: uç olur, `SideConfig` taşır.
  - Envanter olmayan bir blok yüzünde: sabitlenmiş uç, ayarsız.
  - Havada: serbest düğüm ya da joint.
  - Başka bir kıvrımlı hattın düğümünde: dallanma.
- **Aynı tür kuralı:** Kıvrımlı kenar yalnız kendi türündeki borulara bağlanır. Universal ve chemical türleri de
  blok borularındaki kurallarla aynıdır.
- **Graf:** Taşma, kıvrımlı kenarlar ve düğümler üzerinden de yürür. Kaynak/hedef taraması, mesafe sırası,
  round-robin/öncelik ve önbellek geçersizleştirmesi (bir kenar eklenince, silinince ya da düzenlenince ilgili grafın
  düşürülmesi) kıvrımlı parçaları da kapsar.
- **Mesafe:** Hedef sıralamasında mesafe, blok borusunda "adım" sayısıdır. Kıvrımlı kenarın mesafeye katkısı
  builder'ın kararıdır (örneğin bir adım ya da yuvarlanmış uzunluk). Kararı belgeye yazsın.
- **Uç ekranı:** Kıvrımlı ucun ekranı, blok borusu yüz ekranıyla (`PipeMenu`/`PipeScreen`) **aynıdır**. Wrench ve
  sağ tıkla açılır; boş el ya da herhangi bir eşyayla açma kuralı blok borusundaki gibidir. Ekranın "yüz" kavramı,
  kıvrımlı uç için de bir kimlik taşımalı.
- **Kayıt:** Kıvrımlı hatlar dünya başına kaydedilir (bir `SavedData` ya da chunk verisi; builder seçer). İstemciye
  chunk izleme ile senkronlanır. Yeni dünya formatı; eski `flowline_curves.dat` ve `LegacyCurveNotice` davranışı
  korunur.
- **Yükleme sınırları:** Yüklü olmayan chunk'taki kıvrımlı düğümler blok borusundaki `isLoaded` kuralıyla aynı
  şekilde ele alınır. `MAX_NETWORK_SIZE` kıvrımlı düğümleri de sayar.

## Kaldırılacaklar

- Eski isteğe bağlı bağımlılık: `mods.toml` girdisi, `build.gradle` maven girdisi ve test bayrağı, ayrı GameTest
  dünyası.
- Eski köprü:
  - `compat` altındaki köprü sınıfları ve `compat/UniversalChannel`.
  - Köprü mixin'leri ve `flowline.mixins.json` içindeki girişleri.
  - YAML/SnakeYAML jarJar'ı, eğer yalnız köprünün config'i için kullanılıyorsa.
  - `OffhandMode` kanal alanları, `OffhandModePayload`'daki kanal ve protokol sürümü.
- `compat/PipePlacement` yalnız köprünün item'ı için normal yerleşimi sağlıyorsa kalkar ya da sadeleşir.
- Köprünün entegrasyon GameTest'leri kaldırılır, yerine kendi motorun testleri gelir.
- `docs/CURVED_PIPES_TR.md` ve README yeni duruma göre yeniden yazılır.

## Doğrulama

- **Saf Java birim testleri:** Çözücü, yerleşim kuralları ve maliyet. Sayısal örnekler kullanılır:
  eşikler 0.1/0.999, `R` ve `√2` kuralı, `n` sınırları, `ceil(n·h)`.
- **GameTest:**
  - Kıvrımlı hattın aynı ağa katılması: tek Extract ile sandıktan sandığa taşıma.
  - Kıvrımlı uçta Insert/Extract, filtre ve upgrade'in blok yüzüyle aynı çalışması.
  - Maliyet tüketimi ve iade.
  - Koruma reddi.
  - Kayıt/yeniden yükleme.
  - Mevcut blok borusu testlerinin hepsi yeşil kalır.
- **Gözle kontrol (kullanıcı, grafik istemci):**
  - Görünüm ve blok-kıvrımlı geçişi.
  - Önizleme ve hata mesajları.
  - Varlık çarpışması, seçme/kırma, orta tık.
  - Shader uyumu ve FPS.

## Builder'a bırakılanlar

- Modül/sınıf yapısı ve çözücünün sayısal yöntemi; aynı sonuçları veren herhangi bir Newton/Gauss-Newton varyantı.
- Kıvrımlı kayıt biçimi ve ağ paket biçimi.
- Kıvrımlı kenarın mesafeye katkısı (yukarıda).
- Mixin hedef metodları; builder adları mapping'den doğrulamalı.
- Mesh oluşturma ve render tipi (blok borusu `translucent` kullanıyor).
- Kullanıcının Flexi Pipes deposundaki kendi MIT kodu (ör. `CurveSolver`, `PlacementCheck`, `TubeMesh`, `Frames`)
  iskelet olarak kullanılabilir. Davranışı ise yukarıdaki kurallara göre düzeltilmelidir.
