# Flowline kıvrımlı boruları

Flowline 0.3.13'ten itibaren kıvrımlı borular **Flowline'ın kendi özelliğidir**; başka bir mod gerekmez. Ayrı kıvrımlı item, tarif ya da creative girişi yoktur: aynı
`flowline:*_pipe` item'ları hem blok borusu hem kıvrımlı hat döşer. Eşya, sıvı, enerji, universal ve (Mekanism varsa)
kimyasal borular kıvrımlı döşenebilir.

Kıvrımın biçimi, birleşme (joint) kuralları, çarpışma ve maliyet Flowline'ın kendi Java kodundadır.

## El düzeni ve tuşlar

- **Sağ el:** boru her zaman tek blok borusu yerleştirir.
- **Sol el:** mod tuşu (varsayılan **B**) **Build for Me ↔ Kıvrımlı** arasında geçer; seçili mod nişangâhın altında
  yazar. Sağ el boş olmalı (sağ eldeki boru blok koyar, sağ eldeki başka bir blok kendini koyar).

Kıvrımlı modda (hepsi Controls'tan değiştirilebilir):

| Tuş | İş |
| --- | --- |
| Sağ tık | Hattı başlat (blok yüzü, boru yüzü, var olan düğüm, var olan kıvrımlı boru ya da hava), havada nokta ekle, yüzde, düğümde ya da kıvrımlı boruda bitir. Kıvrımlı borunun ortasına bağlanınca boru orada bölünür ve üç kenarlı bir birleşme olur |
| Shift + sağ tık | Havada bitir |
| Sol tık (basılı) | Bakılan kıvrımlı kenarı ya da düğümü blok gibi kır (her modda): survival'da borunun kazma süresi kadar basılı tutulur ve borular yere düşer; creative'de anında kırılır, bir şey düşmez |
| Wrench + Shift + sağ tık | Kıvrımlı kenarı ya da blok ucu olmayan düğümü anında kır; survival'da borular yere düşer. Bloğa bağlı uçta modu değiştirir (Ekle ↔ Çek) |
| Orta tık / Shift + orta tık | Izgara modu ileri / geri: kapalı, eksenler, dış köşeler, iç köşeler, dış kenarlar, iç kenarlar |
| Sol Ctrl basılı | Düğüm/kenar/yüze yapışmayı kapat; yalnız havadaki ızgara |
| Sol Alt basılı + tekerlek | Seçenek çarkı: Sil, Dallandır, Uzat, Taşı, Birleşme yap (force joint), Ayarlar; bırakınca bakılan düğüme/kenara uygulanır |
| Esc ya da B | Yarım hattı (ya da taşımayı) bırak |

- **Taşı:** çarktan Taşı seçilince sonraki sağ tık düğümü bakılan yere taşır (blok yüzüne ya da havaya); sol tık
  vazgeçer.
- **Dallandır:** düğümden yeni hat başlatır; bir kenarda önce oraya düğüm ekler (o düğümden sonra sağ tıkla devam
  edilir).
- Ekranda: mod adı, sağ tıkın ne yapacağı, ızgara, hattın maliyeti (eldeki boru yetmiyorsa kırmızı) ve sunucunun son
  cevabı 3 saniye. Önizleme tüpü istemcinin gördüğü bir sorun varsa kırmızıdır; reddi metinle yalnız sunucu söyler.

## Ağ

- Kıvrımlı hat değdiği blok borularıyla **aynı ağın** parçasıdır. Sandıktaki tek bir Extract tarafı, kıvrımlar dahil
  bütün ağı çalıştırır; ayrı bir Extract/Insert adımı yoktur.
- Uç nereye bağlıysa ona göre davranır:
  - **Envanter yüzü** (sandık, tank, makine): uç bir boru tarafıdır. Aynı ekran, wrench (Shift + sağ tık mod
    değiştirir), Filter Card ve upgrade'ler. Ekranda yan sekmeler yoktur.
  - **Blok borusu yüzü:** o yüz bağlantı olur, uç yalnız taşır.
  - **Başka bir blok yüzü:** uç yalnız hattı tutar.
  - **Hava:** serbest düğüm ya da üç ve daha fazla kenarın birleştiği (ya da keskin dönen) birleşme: dünya
    eksenlerine hizalı kare cam kutu, on iki kenarında 1 px metal çerçeve; kanallar kutunun içine girer, flanş yok.
- Kıvrımlı boru yalnız kendi türündeki ve uyumlu renkteki borulara bağlanır. Boya blok borusundaki gibidir: aynı boya
  ikinci kez silerek kaldırır.
- Hedef sıralamasında kıvrımlı kenarın mesafesi `max(1, maliyet)` adımdır.

## Dünya

- Blok eşyası, çarpışma şekli bir kıvrımla örtüşecekse yerleşmez (önünde bir varlık duruyormuş gibi). Kum/çakıl gibi
  düşen blok, konduğu hücrede kıvrımla örtüşecekse eşya olarak düşer; kıvrım sağlam kalır.
- Bu iki yol dışından kıvrımın geçtiği yere gelen blok (piston, komut, komşusuna haber vermeyen değişimler) bir
  sonraki tick'te o kenarı kırar; borular orada düşer.
- Ucun bağlı olduğu blok kırılırsa uç bağlı kalır ve ayarlarını korur (kullanıcı kararı, 2026-10-19); taşımada o
  tarafta envanter bulunmaz.
- Oyuncular, canlılar, eşyalar ve düşen bloklar kıvrımlı boruların üstünde durur ve onlara çarpar. **Yaklaşık:** eğik
  bir segmentte çarpışma kutusu camın en çok 2 piksel dışına taşar.
- Eğilen oyuncu kıvrımın kenarında durur (kıvrım zemin sayılır); kıvrımın altında çömelen oyuncu ayağa kalkıp
  ona giremez. Yaratık doğma kontrolleri kıvrımları hesaba katmaz (doğrulanmadı, kapsam dışı).
- Koruma modları (claim) yerleşimde ve kırmada blok borusundaki gibi sorulur.

## Eski dünyalar

- Önceki bağımsız Flowline sisteminin `flowline_curves.dat` kayıtları çevrilmez; eski hatlar görünmez ve aktarım
  yapmaz, dosya silinmez. Oyun bunu bir kez mesajla söyler.
- Başka modlarla kurulmuş kıvrımlı hatlar Flowline'a aktarılmaz.
- Yeni kayıt dünya başına `flowline_curved_pipes` (SavedData).

## Doğrulama

- 194 GameTest (normal ve `-PwithMekanism`): geometri ve çözücü, yerleşim kuralları ve hata kodları, maliyet ve
  iade, koruma reddi, kayıt/yeniden yükleme, ağ (tek Extract, mesafe, ayrılma, redstone, yüklenmemiş chunk), kıvrımlı uç
  ekranı/wrench/Filter Card, boyama, dünya değişimleri, varlık çarpışması, istemci karar fonksiyonları.
- Eşya, sıvı ve enerji aktarımı kıvrımdan iki yönde test edilir (blok boru kıvrım üzerinden çeker; kıvrımlı uç blok
  borulara çeker); universal kıvrım üçünü birlikte taşır. Kimyasal kıvrımlar aynı ağ kodunu kullanır ama ayrı oyun
  içi testi yoktur.
- **Gözle doğrulanmadı:** görünüm (cam kanal, raylar, yaka/halka, kare cam birleşme kutusu), blok–kıvrım geçişi, önizleme, HUD,
  tuşlar, seçenek çarkı, havada orta tık, istemcide varlık çarpışması, shader uyumu ve FPS. Bunlar grafik istemcide
  kontrol edilmeli.
