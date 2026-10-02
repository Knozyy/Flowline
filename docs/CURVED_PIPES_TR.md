# Flowline ve gerçek Curvy Pipes motoru

Flowline 0.1.4, **Minecraft 1.20.1** için geliştirilmiştir. **Curvy Pipes isteğe bağlıdır.** Curvy 1.15.8 varsa aynı Flowline eşya/sıvı/enerji borusu item'ları Curvy yerleşimini de destekler. Curvy yoksa iki elde de normal blok yerleşimi çalışır. Ayrı kıvrımlı item, creative girişi veya dönüşüm tarifi yoktur. Çok oyunculuda Curvy hem istemcide hem sunucuda kurulu olmalıdır. Render, önizleme, seçim, radyal menü, hizalama, fizik, uç ayarları, aktarım ve kayıt gerçek Curvy native motorunda çalışır.

## Kullanım

- **Curvy varken sağ el:** normal Flowline eşya/sıvı/enerji borusunu sağ eline al. Curvy'nin kendi HUD'ı, yerleşimi ve düzenleyicisi açılır. Yerleştirme, uzatma, düğüme/hatta bağlama ve yeniden bağlama Curvy'nin kendi sistemidir.
- **Curvy varken sol el:** aynı boruyu sol eline al ve sağ elini boş bırak. Sağ tık normal Flowline blok borusunu yerleştirir. Curvy önizlemesi sol elde açılmaz.
- **Curvy yokken:** aynı item'lar iki elde de normal blok borularını yerleştirir.
- **Sol elde boru + B:** Build for Me, hedef bloktan sana doğru normal blok boruları döşer. Tuş Controls ekranından değiştirilebilir. İlk uygun durumda gösterilen ipucu 8 saniye sürer; `config/flowline-client.toml` içindeki `buildForMeHintSeen` ile saklanır ve başka dünyalarda ya da oyunu yeniden açınca tekrarlanmaz.
- Orta tık Curvy hizalama desenlerini değiştirir; Shift ters yönde çevirir. Düğüm düzenleme, taşıma, keskin eklem ve silme gerçek radyal menüdedir.
- Uçtaki aktarım ayarları ve filtreler Curvy'nindir: Passive / Extract / Retrieve, ID regex ve miktar düzenleme. Şeffaf item borusu için Curvy'nin glass ile değiştirme davranışı kullanılır.
- Normal Flowline boruları sıradan blok yerleşimini, Flowline filtre/upgrade ekranını ve **sol elde boru + B** ile Build for Me özelliğini kullanır.

Curvy'nin yerel lojistik türleri item, fluid ve FE'dir. Universal ve Mekanism chemical boruları Flowline'da normal blok borusu olarak kalır; bunlar için sahte bir Curvy varyantı üretilmez. Yeni kıvrımlı ağlar Flowline blok ağından ayrıdır ve Flowline upgrade'lerini kullanmaz. Curvy'nin kurulu AE2/GregTech/ComputerCraft entegrasyonları kendi ayarlarına göre çalışır.

## Entegrasyon

Curvy'nin Java config yükleme çağrısına mixin eklenir. Kullanıcının YAML'ı bellekte okunur; mevcut borular, tarifler ve diğer mod entegrasyonu ayarları korunarak üç yerel Curvy boru tanımı eklenir. **Kullanıcının `curvy_pipes.yaml` dosyası yazılmaz.** Yalnızca Curvy'nin kendisi kendi default dosyasını normal şekilde üretir.

Native tanımların gerçek item kayıtları mevcut `flowline:item_pipe`, `flowline:fluid_pipe`, `flowline:energy_pipe` kimliklerine bağlanır. Curvy kuruluysa bu item'lar gerçek `BuiltInPipeItem` nesneleridir; normal blok yerleşimi de aynı nesneyle çalışır. Curvy'nin stok toplama, tüketim ve iade işlemleri doğrudan oyuncunun aynı boru stack'lerini kullanır. Forge kayıt eşlemesi yenilendiğinde normal blokların item eşlemesi tekrar bağlanır. Claim/koruma olayları normal yerleşim ve Build for Me için çalışmaya devam eder.

Native palette adları önceki Curvy kayıtlarını korumak için `flowline_item_pipe`, `flowline_fluid_pipe`, `flowline_energy_pipe` olarak kalır; bunlar ayrı kayıtlı item'lar değildir. Önceki çift-item sürümünden kalan `curvy_pipes:flowline_*_pipe` envanter kayıtları mevcut Flowline item'larına eşlenir. Çap 0.2 blok; varsayılan item hızı 16/30 item/tick, fluid hızı 1000/30 mB/tick, enerji hızı 8000 FE/tick. Bunlar Curvy throughput değerleridir; Flowline'ın adaptif aralık/upgrade ayarlarıyla eşdeğer davranış iddiası yoktur. Malzemeler Flowline'ın sarı/mavi/turuncu paletini kullanır, mesh tamamen Curvy'nindir.

Java köprüsündeki hook sürüme bağlı olduğu için isteğe bağlı Curvy entegrasyonu tam **1.15.8** sürümüne sabitlenmiştir. Kaynak kod veya native binary kopyalanmaz. [Gerekli Curvy sürümü ve resmi Maven koordinatı](https://www.curseforge.com/minecraft/mc-mods/curvy-pipes/files/8822563).

## Eski dünyalar

Önceki bağımsız Flowline sisteminin `flowline_curves.dat` kayıtları Curvy'nin native kayıt formatına otomatik çevrilmez. **Eski hatlar bu sürümde görünmez ve aktarım yapmaz.** Dosya silinmez veya üzerine yazılmaz. Eski hatları ve upgrade'leri geri almak için önce eski Flowline sürümüyle dünyayı açıp hatları sökmek gerekir; sonra yeni motorla yeniden kurulur. Curvy'nin zaten var olan `curvy_pipes.dat` kayıtları kendi motoru tarafından normal şekilde yüklenir.

## Dağıtım ve doğrulama

`gradlew build` sonucu dağıtım dosyası `build/libs/flowline-0.1.4+1.20.1.jar`; YAML kütüphanesi içindedir. `-slim.jar` dağıtım için kullanılmaz. Kıvrımlı özellik isteniyorsa Curvy Pipes JAR'ı ayrıca kurulur. Geliştirme ve GameTest çalıştırmaları varsayılan olarak Curvy içermez; `-PwithCurvy` eklenince gerçek motor yüklenir.

Doğrulama testleri aynı item'ın normal blok yerleştirmesini, su içinde yerleşmesini, tüketimini, kırılınca geri gelmesini ve koruma modları tarafından reddedilmesini kontrol eder. Curvy yüklüyken native motorun mevcut Flowline kimliklerini tanıması, gerçek envanter stack'lerini tüketip iade etmesi, sağ/sol el ayrımı ve fazladan item/tarif bulunmaması da sınanır. Grafik istemcisi açılmadı; oyun içi render, shader uyumu ve FPS bu çalışmada gözlemlenmedi. Instance'a JAR kurulmadı.
