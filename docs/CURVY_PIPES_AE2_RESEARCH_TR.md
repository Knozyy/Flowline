# Curvy Pipes ve AE2 araştırması

İnceleme tarihi: 2026-10-02. Referans instance: `C:\MC\Instances\deneme (1)`.

Bu not, bir sonraki Flowline özelliğine hazırlık için kurulu JAR'ların Java bytecode'u, dil kaynakları, gerçek config dosyaları, dünya kayıtlarının NBT zarfı ve mod yazarlarının belgeleri üzerinden hazırlanmıştır. Oyun açılarak davranış testi yapılmadı. Aşağıda doğrudan bulgular ile Flowline için yapılan çıkarımlar ayrı belirtilmiştir.

## 1. Kurulu sürümler ve etkin dosya ayarları

| Bileşen | Instance'taki dosya | Sürüm |
| --- | --- | --- |
| Curvy Pipes | `mods/curvy_pipes-1.20.1-1.15.8.jar` | 1.15.8 |
| Applied Energistics 2 | `mods/appliedenergistics2-forge-15.4.10.jar` | 15.4.10 |

JAR SHA-256 değerleri:

- Curvy Pipes: `49b4670a7a7e16581c5b407f2a84eaf6b0acf8bc26f87a5f76f5e0adeb281a0c`
- AE2: `fbfee05c6674cb6b00fe425e945ca4818d41eb34069b60dcb4a36221a0390fcc`

İncelenen kullanıcı config'i `config/curvy_pipes.yaml`; paket varsayılanı `config/curvy_pipes_default.yaml` ile aynı değil. Özellikle hızlar artırılmış ve kalınlıklar değiştirilmiş. Kullanıcı dosyasında:

- `ae2.cables: OffHand`: AE2 kablosu yan elde kullanıldığında Curvy varyantı yerleştiriliyor. Yeni kablo item'ı gerekmiyor. `AnyHand` ve `Disable` diğer desteklenen seçenekler.
- `ignore_unknown_pipes: false`.
- AE2'nin `config/ae2/common.json` dosyasında `channels: "default"`.

Curvy kullanıcı config'indeki değerler; bunlar AE2 kablolarının kanal kapasitesi değil, Curvy'nin kendi borularının aktarım oranlarıdır:

| Boyut | Çap, blok birimi | Item/tick | Fluid mB/tick | Energy FE/tick |
| --- | ---: | ---: | ---: | ---: |
| Tiny | 0.1 | 1 | 100 | 10,000 |
| Small | 0.1 | 10 | 1,000 | 100,000 |
| Medium | 0.2 | 100 | 10,000 | 1,000,000 |
| Large | 0.3 | 1,000 | 100,000 | 10,000,000 |
| Huge | 0.5 | 10,000 | 1,000,000 | 100,000,000 |

Tick başına oran ile uçta seçilen işlem aralığı farklı ayarlardır. Gerçek duvar saati hızı sunucunun TPS değerine bağlıdır.

## 2. Kıvrım ve kullanıcı etkileşimi

Curvy Pipes, blok merkezlerini birleştiren borular yerine serbest konumlu hatlar kullanıyor. Aynı blok hacminde ayrı hatlar bulunabiliyor; yerleşim mevcut blokların boş geometrisini de kullanabiliyor. Boruların fiziksel çarpışması var. Mod yazarı bunları modun temel davranışı olarak açıklıyor. [Yazarın açıklaması](https://www.curseforge.com/minecraft/mc-mods/curvy-pipes)

Kurulu JAR'ın `assets/curvy_pipes/lang/en_us.json` kaynağında şu işlem durumları doğrudan mevcut:

1. Havada veya blok üzerinde başlangıç.
2. Ara düğüm ekleme; mevcut boru üzerine düğüm yerleştirme.
3. Havada/blok üzerinde bitirme; mevcut hatta veya düğüme bağlama.
4. Mevcut düğümden dal oluşturma ve hattı uzatma.
5. Düğümü taşıma/silme ve bağlı ucu yeniden konumlandırma.
6. `Force Joint` / `Unforce Joint`: düğümü zorunlu eklem yapma veya kaldırma.
7. Bağlı uçta aktarım ayarları.

`Force Joint` ile dallanma oluşturmadan keskin köşe yapılabiliyor. Bir bağlı ucu başka bloğa taşımak ayarlarını koruyabiliyor; tamamen ayırmanın ayar kaybı oluşturacağı kurulu dil dosyasında ayrıca bildiriliyor. [Sürüm geçmişi](https://github.com/cyb0124/CurvyPipes-Issues/blob/master/CHANGELOG.md)

İsteğe bağlı hizalama desenleri kurulu kaynaklarda `Off`, `Axial`, `Corner+`, `Corner-`, `Edge+`, `Edge-`. Orta tık ile desen seçiliyor. Kurulu 1.15.8 için önemli sürüm ayrıntısı: 1.13.0'dan itibaren orta tık hizalama seçimini block-pick işlemine tercih ediyor; artık mutlaka boşluğa nişan almak gerekmiyor. Sneak ile desenler ters yönde çevrilebiliyor. [Sürüm geçmişi](https://github.com/cyb0124/CurvyPipes-Issues/blob/master/CHANGELOG.md)

Java köprüsünde attack/use-item girdileri ayrı native işlemlere iletiliyor; yerleşim, taşıma ve düzenleme için bağlama göre değişen tuş adları HUD'a ekleniyor. Bu nedenle bütün işlemlere tek bir sabit tıklama tarifi verilmedi. Ayrıntılı tuş eşlemesi oyun içinde HUD üzerinden doğrulanmalı.

Dil kaynağındaki geçerlilik hataları: yetersiz malzeme, erişim dışı konum, geçersiz şekil/nesne, çok dar dal açısı, eklem içinde düğüm, blokla veya başka boruyla kesişme. Önizlemede bir çizgi göstermek bu kuralların tamamını karşılamıyor. Tam açı toleransları ve geometrik kesişme algoritması native tarafta.

## 3. Curvy'nin kendi lojistik sistemi

Kurulu dil kaynağında üç uç modu tanımlı:

| Mod | Anlam |
| --- | --- |
| Passive | Aktif uçların aktarım yapabildiği pasif bağlantı |
| Extract | Bağlı pasif uçlara gönderme |
| Retrieve | Bağlı pasif uçlardan çekme |

Item ve fluid filtreleri; deny seçeneği; namespaced ID üzerinde güvenli regex; tür başına ve toplam miktar düzenleme bulunuyor. Miktar düzenleme, çıkarımda alt sınırın altına düşmeyi ve eklemede üst sınırın aşılmasını engellemeyi hedefliyor. İşlem aralığı 1–1200 tick. Bunlar kurulu JAR'ın UI açıklamaları; karmaşık filtre birleşimlerinin öncelik algoritması çıkarılmadı.

Java tarafında item/fluid kodlama ve capability çözümleme köprüleri, FE için `IEnergyStorage` çözümleme ve blok yüzü bilgisi bulunuyor. Hat uçları gerçek envanter/depo/yüz bağlantılarına bağlanıyor; eğri çiziminin kendisi bir envanter oluşturmuyor.

Kurulu item açıklaması aktarımın anlık ve iç tamponun bulunmadığını söylüyor. Yazar da şeffaf item borusunda görülen hareketin geçmiş aktarımın görseli olduğunu belirtiyor. Hat üzerinde görülen item'ı tekrar hedefe eklemek çift aktarım oluşturur. Yazarın lojistik açıklamasında seri dar kesit darboğazı, paralel kapasite paylaşımı ve ara chunk'lar yüklenmemişken bağlı makineler yükleniyorsa aktarım belirtiliyor. Bunlar oyun içinde bu instance'ta ölçülmedi ve AE2 chunk davranışının tamamına genellenmemeli. [Yazarın açıklaması](https://www.curseforge.com/minecraft/mc-mods/curvy-pipes)

## 4. AE2'nin kıvrımlı kablosu nasıl çalışıyor?

Kıvrımlı AE2 kablosunu sağlayan bileşen Curvy Pipes'ın `cyb0124.curvy_pipes.compat.AECompat` entegrasyonu. AE2 tek başına bu serbest eğri yerleşim sistemini sağlamıyor. Kurulu sınıfta Quartz Fiber ile bütün AEColor değerleri için Glass, Covered, Smart, Dense Covered ve Dense Smart kayıtları var.

### Gerçek ME ağına bağlanma

`AECompat.addNode` doğrudan `GridHelper.createManagedNode` kullanıyor. Oluşturulan düğümün kendi idle power değeri 0 ve görsel temsil item'ı Curvy kablosu/eklemi. `create(level, null)` çağrısı, bir standart kablo BlockEntity'sinin konumu üzerinden oluşturulmadığını gösteriyor. Bu, bütün ME ağının enerji maliyetinin sıfır olduğu anlamına gelmiyor.

Bu Java metoduna gelen kapasite baytı `0` ise `CANNOT_CARRY`, `1` ise `PREFERRED`, diğer değerlerde `DENSE_CAPACITY` bayrağı seçiliyor. Hangi eğri unsurunun hangi baytla oluşturulduğunun tüm kararları native tarafta; bu parametre kayıt metodundaki kablo türü numarasıyla karıştırılmamalı.

`addNodeConn` mevcut bağlantıyı kontrol ediyor, yoksa `GridHelper.createConnection` çağırıyor. Sonuç gerçek AE2 ağ grafiğinin parçası. Item/fluid capability'sine dönüştürülen sahte bir ME bağlantısı değil.

### Normal ve dense kanal kapasitesi

AE2'nin bu instance'taki `default` kanal modunda standart kablo kapasitesi 8, dense kapasitesi 32 kanal. Smart tip kanal göstergesi ekliyor. Kanal, item sayısı veya geometrik kontrol noktası sayısı değil, AE2'nin ağ bağlantı kaynağıdır. Renk bağlantı uyumluluğunu etkiler; Fluix diğer renklerle bağlantı kurabilir. [AE2 1.20.1 kablo rehberi](https://guide.appliedenergistics.org/1.20.1/items-blocks-machines/cables)

Curvy Java kayıtları renkleri native köprüye iletiyor. Native renk eşleştirme kodu bu incelemede çıkarılmadı; bütün renk/kesişim kombinasyonları test edildiği iddia edilmiyor.

### Smart göstergesi

`AECompat$NodeOwner.onStateChanged`:

- Düğüm enerjiliyse `IGridNode.getUsedChannels()` değerini alıyor.
- Enerjisizse 0 kullanıyor.
- Değer değiştiğinde bir `IntConsumer` üzerinden native görsel overlay indeksini güncelliyor.

Yani smart çizgileri AE2'nin gerçek hesaplamasından besleniyor. Java listener'ın `onSaveChanges` gövdesi boş; bu tek başına Curvy hatlarının kaydedilmediği anlamına gelmiyor, hat kaydı ayrı dünya verisiyle yapılıyor.

### Quartz Fiber

`addEnergyPair` iki ayrı managed node oluşturuyor; ikisinde de `CANNOT_CARRY` var. Birbirinin energy service'ine `IEnergyOverlayGridConnection` üzerinden erişim veriyor. Java köprüsündeki bu yol, iki ME kanal ağını tek kanal grafiğine birleştirmeden AE enerjisini paylaşma davranışını sağlıyor. FE energy pipe ile aynı şey değil.

### Multipart ve doğru uç

`addBlockConn` önce hedef konumdan `GridHelper.getNodeHost` alıyor. Hedef `IPartHost` ise seçili yüzün part'ını arıyor; bir bayrağa göre part'ın `getExternalFacingNode()` veya `getGridNode()` düğümüne bağlanıyor. Part bulunamazsa host'un yüz düğümünü kullanıyor. Bu iki part düğümü özellikle dış/iç ağ ayrımı olan parçalarda aynı kabul edilemez.

Yazarın 1.11.3 değişikliği, bus/terminal parçalarının arka yüzü yanında yan yüzlerinden bağlantıyı da ekliyor. Aynı blok hacminde birbirinden bağımsız hatlar mümkün. Ancak fiziksel yakınlık ile elektriksel bağlantı aynı şey değil; endpoint hangi part/yüz/ağ düğümüne bağlandığını korumalı. Her kablo tipi ve part çifti için native doğrulama kuralları bu araştırmada eksiksiz çıkarılmadı. [Curvy AE2 açıklaması](https://modrinth.com/mod/curvy-pipes)

## 5. Kayıt, chunk takibi ve çizim

Kurulu `CommonHandler` köprüsünde `loadLevel`, `saveLevel`, `unloadLevel`, `chunkLoaded`, `chunkUnloaded`, `watchChunk`, `unwatchChunk` ve server tick işlemleri var. Level yüklenirken `DimensionDataStorage` üzerinden `curvy_pipes` isimli `SavedData` yükleniyor.

`CommonHandler$1` kayıtta native `saveLevel` çıktısını NBT'nin `0` adlı byte array'ine yazıyor. Dünya dosyalarının salt okunur zarf incelemesi de bunu doğruladı:

| Dünya dosyası, `saves/` altında | `data/0` byte array uzunluğu |
| --- | ---: |
| `world/data/curvy_pipes.dat` | 104,956 |
| `world/DIM-1/data/curvy_pipes.dat` | 568 |
| `world/DIM1/data/curvy_pipes.dat` | 283 |
| `Deneme/data/curvy_pipes.dat` | 0 |

Bu değerler boru veya düğüm sayısı değil. İç native binary kayıt formatı çözülmedi. Kayıtların boyutlara ayrıldığı ve hat verisinin yalnızca tekil boru BlockEntity NBT'sinden oluşmadığı doğrulandı.

Client köprüsünde dünya render aşamasına katılma, translucent çizim, GUI/HUD ve entity çizimi var; ayrıca GPU vertex buffer oluşturma/çizme ve ışık sorgulama yardımcıları bulunuyor. Blok collision/outline şekilleri double koordinat kutuları halinde native tarafa sunuluyor. Asıl eğri mesh'i, yerleşim algoritması ve tick işlemi native.

Modun temel işlevlerinin Rust/native olması yazar tarafından da açıklanıyor. Kurulu JAR `x64.bin` ve `aarch64.bin` içeriyor. Bu incelemede native kod çalıştırılmadı veya tümü tersine çevrilmedi. [Yazarın teknik açıklaması](https://modrinth.com/mod/curvy-pipes)

## 6. Flowline için sonuçlar — mimari çıkarım

Mevcut Flowline `PipeNetwork` grafiğini `BlockPos` ile indeksliyor; altı komşu yönündeki `Conn.PIPE` bağlantılarını izliyor. Aktarım hedeflerinin animasyon yolu `List<BlockPos>`. Yeni `FluidFlowPayload` da ardışık konumların Manhattan uzaklığını 1 olarak doğruluyor.

Bu nedenle serbest Curvy davranışı istenirse yalnızca boru modelini yuvarlatmak yetmez. Şunlar bağımsız tasarlanmalı:

| Alan | Gereken ayrım |
| --- | --- |
| Geometri | Dünya içindeki kesirli konumlar, kontrol noktaları, eğri kesitleri ve eklemler |
| Ağ | Kalıcı node/edge kimlikleri; aynı blokta birden fazla bağımsız hat; açık bağlantı bilgisi |
| Endpoint | Hedef blok, yüz, temas konumu; AE2 varsa part ve iç/dış ME düğümü |
| Seçim | Boru, ara düğüm, eklem ve bağlı ucun ayrı hedeflenmesi |
| Fizik | Eğri/blok/boru çarpışması, dar dal açısı ve geçerli yerleşim |
| Kayıt ve senkronizasyon | Boyut ve chunk yaşam döngüsü; değişen hatların izleyen client'lara gönderilmesi |
| Animasyon | Mantıksal aktarım ile görsel yol ayrı; eğri üzerindeki ilerleme; mevcut blok yolu paketinden geçiş |
| AE2 | Managed node/connection oluşturma ve kaldırma; ağ ayrışması; gerçek kanal/enerji durumu |

Bir çizginin diğerine yakın geçmesi otomatik bağlantı sayılmamalı. Bir düğümün taşınması geometriyi değiştirebilir; başka makineye bağlanan uç ise endpoint'i ve topolojiyi değiştirir. Bu ayrım hem düzenleme sırasında ayar korunması hem ağın doğru güncellenmesi için önemli.

Flowline borusunun Curvy tarzında yerleşmesi, mevcut Curvy Pipes moduyla entegrasyon ve Flowline'a gerçek ME kablosu eklenmesi üç ayrı uygulama kapsamı. Kullanıcının sonraki özellik tanımı gelmeden bunlardan biri varsayılmamalı. AE2 desteği seçilirse opsiyonel yükleme ve AE2 olmadan çalışma ayrıca korunmalı.

Sonraki uygulama için önemli doğrulama senaryoları: aynı hacimde bağımsız hatlar, dal oluşturma/kesme, kayıt sonrası kimlikler, dimension değişimi, chunk sınırında endpoint, uç yeniden bağlama, geçersiz kesişim, smart kanal sayısı, quartz fiber ile kanal ayrımı ve item/fluid animasyonunda çift aktarım olmaması.

## 7. Kanıtların sınırı ve yeniden inceleme

Doğrudan doğrulananlar: kurulu sürümler/hash'ler, dosya ayarları, UI işlem durumları, Java AE2 köprüsü, NBT kayıt zarfı ve mevcut Flowline'ın blok grafiği.

Henüz doğrulanmayanlar: native eğri türü (Bézier/spline olduğu iddia edilmiyor), tam kesişme matematiği, malzeme maliyetinin uzunluk formülü, native kayıt şeması, bütün node yaşam döngüsü kararları, bu instance'ta FPS/TPS ve shader davranışı, her part/kablo kombinasyonunun oyun içi sonucu.

Yerel bytecode dökümleri geçici/ignore edilen `build/research/curvy/` altında: `ae-compat.javap.txt`, `common.javap.txt`, `client.javap.txt`. Silinirse kurulu JAR üzerinden JDK 17 `javap -p -c` ile tekrar üretilebilir. Kalıcı bulgular bu dosyada tutuluyor. Araştırma sırasında instance config'i, JAR'ları ve dünya kayıtları değiştirilmedi.
