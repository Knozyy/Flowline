# Flowline kıvrımlı borular

Curvy Pipes davranışından yararlanılarak Flowline içinde bağımsız bir serbest yerleşim sistemi eklendi. Curvy Pipes veya AE2 moduna bağımlılık yok. Eğriler Flowline'ın kendi cubic Hermite geometrisiyle oluşturuluyor; Curvy'nin kapalı native kodu kullanılmıyor.

## Kullanım

1. Flowline borusunu **sağ eline** al. Yerleşim HUD'ı kendiliğinden görünür.
2. Sağ tıkla blok üzerinde veya havada başlangıç yap.
3. Havaya sağ tıklayarak ara düğümler ekle. Mevcut iki komşusu olan düğümler hattı yumuşak biçimde kıvırır.
4. Bir bloğa veya mevcut düğüme tıklayarak hattı bağla ve bitir. Havada bitirmek için varsayılan **Enter** tuşunu kullan.
5. **X** düzenlemeyi iptal eder; zaten döşenmiş hatları silmez. Başlangıç olarak kalmış bağlantısız geçici düğüm temizlenir.

Tekerlek, hedef noktanın mesafesini 1–7.5 blok arasında değiştirir. Orta tık hizalamayı kapalı, 1/8, 1/4 ve 1/2 blok aralıkları arasında çevirir. Shift+tekerlek normal hotbar seçimidir.

**Shift+sağ tık** mevcut blok boru yerleşimini kullanır. **Sol elde boru + B**, mevcut Build for Me ile blok borularını döşer. Tuşlar Controls > Flowline'dan değiştirilebilir; HUD gerçek atanmış tuş adını gösterir.

5 blok veya daha uzaktaki bir bloğa, sunucunun Build for Me menzili içinde bakarken kısa tanıtım görünür. Boru sol eldeyse tuş ve işlemin sonucu açıklanır; yalnızca sağ eldeyse önce sol ele alma yönergesi verilir. Oyuncu bir ekran açtığında veya boruyu bıraktığında ipucu görünmez.

## Düzenleme ve uç ayarları

Boruyla **Ctrl+sağ tık**, nişan alınan düğüm/kesit için küçük bir düzenleme menüsü açar. Buradan dal/uzatma, düğüm taşıma, kesite düğüm ekleme, keskin eklem açma/kapatma ve kaldırma yapılabilir. Bir düğüme doğrudan tıklamak da yeni dalın başlangıcı olur.

Makineye bağlı uçta boş el veya anahtarla sağ tık, mevcut Flowline filtre ve upgrade ekranını açar. Shift+sağ tık uç modunu **Insert → Extract → Off → Insert** olarak değiştirir. Yeni hattın ilk bağlı ucu Extract, sonraki bağlı uçlar Insert olur; gerektiğinde modları değiştir.

Bağlı uç başka bloğa/yüze taşındığında filtre, öncelik ve upgrade'ler korunur. Ayarların kaybolmaması için bağlı ucun taşınması yine bir bloğa yapılmalıdır. Aynı blok hacminde bağımsız hatlar bulunabilir; bağlantı yalnızca açık node/edge ilişkisiyle oluşur. Tüplerin fiziksel olarak kesişmesine izin verilmez.

Survival'da eğri uzunluğu yukarı yuvarlanarak her blok uzunluk için bir boru kullanılır. Geometri değişirse yalnızca ek uzunluk maliyeti alınır; kısaltma mevcut hat maliyetini düşürmez. Kaldırırken gerçekten harcanmış borular ve uç upgrade'leri iade edilir. Creative yapımı hat malzeme iadesi üretmez. Başarısız yerleşim malzeme tüketmez.

Kıvrımlı hatları sahibi veya operatör düzenleyebilir. Sunucu erişim mesafesini, bakış yönünü, chunk'ları, dünya sınırını, blok/tüp çarpışmasını ve Forge yerleştirme olayını kontrol eder. Dünya kayıtları boyut başına `flowline_curves.dat` altında saklanır. Boru içinde görülen hareket gerçek aktarımın ardından gönderilen animasyondur; aktarımı ikinci kez yapmaz.

## Görünüm

Mat, sakin renkler: item sarı, fluid mavi, energy turuncu, universal mor; chemical yeşil. Gövdede ince şeffaf pencereler var. Dokularda ışıltı, neon, metal parlaması veya dış gövdede gürültü yok. Hem normal blok boruların dokuları hem kıvrımlı mesh bu yönde düzenlendi. Eğriler dünya ışığıyla çiziliyor; fluid kendi rengini/dokusunu kullanıyor.

## Kapsam ve doğrulama

Kıvrımlı grafikte item, fluid, energy ve universal aktarım mevcut Flowline aktarım kodunu kullanır. Mekanism kuruluysa chemical tipi aynı opsiyonel aktarım köprüsüne gider; bu değişiklikte Mekanism ile ayrıca çalıştırılmadı.

Bu sürümde kıvrımlı grafiğin uçları doğrudan makinelere bağlanır. Normal blok-boru grafiğine bağlayarak iki grafiği birleştirme ve AE2 ME kablosu oluşturma uygulanmadı. Kıvrımlı uçta redstone giriş kontrolü var; bağımsız bir blok olmadığı için redstone **çıkış** düğmesi gösterilmez. Dye/facade ve config-card etkileşimleri mevcut blok borular içindir.

Yerleştirme erişimi 8, tek kesit uzunluğu 16 blokla sınırlı. Dünya başına 4096 düğüm/8192 kesit; ağ boyutu mevcut `maxNetworkSize` ayarına bağlı. Client görünümü yakındaki en fazla 512 kesit/1024 düğümle sınırlı; bu sınıra ulaşıldığında uyarı görünür. Geometriler ve chunk bazında sunucu çarpışma adayları önbelleklenir.

2026-10-02 doğrulaması: Java derlemesi başarılı; 57 GameTest'in tamamı geçti. Kıvrımlı borular için 11 test: bağımsız/dallanan/kesilen grafik, kayıt ve ayarların korunması, vanilla çarpışma sorgusu, blok ve tüp kesişimi, malzeme hesabı, forged konum/yanlış araç, endpoint yüz değiştirme, creative hesabı, gerçek item/fluid/energy aktarımı, makine yüzlerinden başlayıp bitirme ve paket sınırları.

Oyunda kalan görsel kontroller: sağ elde otomatik HUD, yeniden atanmış tuşlar, küçük GUI ölçeğinde açıklamalar, eğri seçimi/önizleme, pencerelerden item ve su/lav görünümü, bağlantı eklemleri ve yoğun ağ FPS. Oyun istemcisi bu çalışma sırasında açılmadı; bu kontrollerin geçtiği iddia edilmiyor.
