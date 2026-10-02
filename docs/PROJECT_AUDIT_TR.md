# Flowline hata incelemesi — 2 Ekim 2026

İnceleme başlangıcı: `1.20.1`, `d18dee9d2c703474c5eda89cc2a94e7fe2278beb`.

Boru aktarımı, ağ keşfi, curvy bağlantılar, filtreler, regülatörler, redstone,
sunucu paketleri, menü yaşam döngüsü, kartlar, wrench, Build for Me, animasyonlar,
isteğe bağlı entegrasyonlar ve kaynak dosyaları gözden geçirildi.

## Düzeltilen hatalar

| Alan | Önceki davranış | Düzeltme |
| --- | --- | --- |
| Eşya aktarımı | Hedefin reddettiği ve kaynağa geri dönen eşyalar taşınmış sayılıyor; bütçe ve animasyon yanlış hesaplanıyordu. | Gerçek kabul edilen miktar geri koyma işleminden önce hesaplanıyor. Kalan bütçe diğer hedeflerde kullanılabiliyor. |
| Sıvı aktarımı | İlk tanktaki sıvı kaynak/hedef filtresine takılınca sonraki uygun tanklar denenmiyordu. | Tanklar tek tek inceleniyor; uygun sıvı türüyle drenaj simülasyonu ve aktarım yapılıyor. |
| Dengeli eşya dağıtımı | Kaynakta tutulacak rezerv de paylaştırılabilir miktara dahil ediliyordu. | Yalnızca rezerv üzerindeki miktar paylaştırılıyor; aynı eşyanın rezervi birden fazla slot için tekrar sayılmıyor. |
| Regülatör toplamları | Büyük sanal envanter ve çok tanklı depolarda toplamlar `int` sınırını aşabiliyordu. | Toplamlar `long` ile hesaplanıyor; aktarım miktarları sınırlandırılıyor. |
| Enerji bütçesi | Yüksek fakat geçerli ayarlarda çarpım ve dengeli bölüştürme taşabiliyordu. | Bütçe çarpım aşamalarında sınırlandırılıyor; pay hesaplaması toplama taşması üretmiyor. |
| Tıkanmış redstone çıktısı | Yalnızca rezerv kalan kaynaklar tıkanmış sayılabiliyor; filtreye uygun sonraki sıvı tankları gözden kaçabiliyordu. | İş kontrolü filtreyi, çıkarılabilir miktarı ve rezervi dikkate alıyor. |
| Curvy redstone darbesi | Bekleme süresi sırasında gelen darbe tüketilebiliyor ve aktarım yapılmıyordu. | Normal boruların yükselen kenar takibi kullanılıyor; darbe bekleme süresinden bağımsız bir kez çalışıyor. |
| Curvy enerji | Çalışan enerji bağlantıları adaptif bekleme nedeniyle aradaki tikleri atlıyordu. | Başarılı aktarım sonrasında her tik çalışıyor. |
| Curvy çarpışma | Vanilla çarpışma sağlayıcısına dünya koordinatları yerine yerel şekil veriliyordu. | Şekil dünya koordinatlarında iletiliyor; gerçek aşağı hareketin boruda durduğu test ediliyor. |
| Capability önbelleği | Önceden önbelleğe alınmış capability, chunk boşaltıldıktan sonra kullanılabiliyordu. | Önbellekten okurken de konumun yüklü olduğu kontrol ediliyor. |
| Menü ayar paketleri | Geçerliliğini kaybetmiş açık menü üzerinden ayar yazılabiliyordu. | Sunucuda `stillValid` kontrolü ekleniyor. |
| Build for Me | İnşa yetkisi olmayan oyuncu pahalı yol aramasını tetikleyebiliyor; tekrar istekleri sınırlanmıyordu. | Yol aramadan önce oyuncu yetkisi kontrol ediliyor; oyuncu başına 20 tik istek aralığı uygulanıyor. |
| Wrench kaydırması | Kaydırma paketi eğilme ve inşa yetkisi koşullarını yeniden denetlemiyordu. | Sunucuda oyuncu durumu, eğilme, eldeki araç, mesafe, yüklü konum ve etkileşim izni denetleniyor. |
| Kart filtresi | Bozuk NBT içindeki negatif veya aşırı büyük slot numarası hata ya da gereksiz bellek büyümesi oluşturabiliyordu. | Desteklenen slot sınırının dışındaki kayıtlar atlanıyor. |
| Paket listeleri | Geçersiz liste uzunlukları sessizce kesiliyor veya negatif değerlerle işleniyordu. | Item travel ve filter page paketleri geçersiz uzunlukları ayırma yapmadan reddediyor. |
| Uzun görsel yollar | Kesilmiş yol kaynak boruya ulaşmasa da animasyon yolu olarak kullanılabiliyordu. | Kaynağa ulaşmayan veya paket sınırını aşan yol için animasyon gönderilmiyor. |
| Dayanıklılık filtresi | Yüksek dayanıklılık değerinde yüzde hesabının çarpımı taşabiliyordu. | Ara hesaplama `long` kullanıyor. |
| Eşya animasyonu | Boyut değişiminde eski animasyonlar yeni dünyada kalabiliyordu. | İstemci dünya nesnesi değişince ve çizim kapatılınca kayıtlar temizleniyor. |
| Curvy düzenleme ekranı | Ekran açılmadan önce silinen düğüm için ayarlar oluşturulurken null erişimi oluşabiliyordu. | Düğüm/kenar hâlâ mevcut değilse ekran kapatılıyor. |

## Doğrulama

- JDK 17, Minecraft 1.20.1, Forge 47.3.0.
- `gradlew.bat build runGameTestServer --offline --console=plain`: başarılı.
- 71 zorunlu GameTest geçti. Önceki 57 teste 14 yeni test eklendi; mevcut çarpışma testi gerçek hareket kontrolüyle genişletildi.
- Yeni testler: reddedilen/kısmen kabul edilen eşya, büyük envanter, rezervin dengeli paylaşımı, kaynak ve hedef sıvı filtresi, rezerv/redstone iş kontrolü, maksimum enerji bütçesi, yüksek enerji ayarı, bozuk paket uzunluğu, bozuk kart slotu, wrench yetkisi, curvy darbe ve curvy enerji sürekliliği.
- 84 JSON/pack metadata belgesi ayrıştırıldı. Yinelenen JSON anahtarı veya eksik Flowline model/doku referansı bulunmadı.
- İngilizce dil dosyasındaki 304 anahtarın tamamı Türkçe dosyada mevcut. Türkçedeki 29 ek ayar açıklaması İngilizcede config yorumlarına geri dönüyor; mevcut fallback davranışı korundu.
- `git diff --check`: başarılı (Windows satır sonu dönüşümü uyarıları dışında hata yok).
- Üretilen paket: `build/libs/flowline-0.1.1+1.20.1.jar`.

## Doğrulama sınırları

Başsız test çalışması bu düzeltmelerin kapsadığı sunucu davranışlarını doğrular.
İstemci ekranlarının çizimi, boyut değişimi animasyonları ve JEI/EMI/Jade/Mekanism
ile gerçek mod paketi içindeki çalışma ayrıca oyun içi kontrol gerektirir.
Bu inceleme bütün olası hataların yokluğunu garanti etmez.

GUI yeniden tasarlanmadı. Oyun penceresi açılmadı; test instance'ına JAR kurulmadı.
Commit ve push, inceleme sonrasında kullanıcının ayrı isteğiyle yapılır.
