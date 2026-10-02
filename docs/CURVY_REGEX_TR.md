# Curvy Pipes regex filtresi

Kurulu sürüm: Curvy Pipes 1.15.8, Minecraft 1.20.1. İncelenen JAR: `C:\MC\Instances\deneme (1)\mods\curvy_pipes-1.20.1-1.15.8.jar`.

## Kesinleştirilen davranış

Kurulu UI kaynağı regex'in namespaced kayıt kimliğini eşleştirdiğini söylüyor. Örnek: `minecraft:iron_ingot`. Görünen/çevirilmiş item adı, lore veya stack'in NBT verisi eşleşme metni olarak belirtilmiyor. Item ve fluid filtrelerinin ayrı UI alanları bulunuyor. Regex filtresinin kendisi aktarım miktarı veya boru güzergâhı seçmiyor; aktarılabilecek türlerin seçimine katılıyor.

Native `x64.bin` içindeki derleme yolu dizgilerinde `regex-1.12.2`, `regex-automata-0.4.13` ve `regex-syntax-0.8.8` bulundu. Bu, Rust regex ailesinin kullanıldığını doğruluyor. Bu incelemede native kod yürütülmedi.

Modun sürüm geçmişinde derlenmiş regex boyutunun 1 MiB ile sınırlandığı açıklanıyor. Bu sınır yazılan karakter sayısına eşit değil; derlenmiş desenin boyutu. Kurulu sürüm 1.14.0'daki nokta jokeri düzeltmesini içeriyor. [Yazarın sürüm geçmişi](https://github.com/cyb0124/CurvyPipes-Issues/blob/master/CHANGELOG.md)

## Kullanılabilecek örnekler

Tam ID eşleşmesi için başlangıç ve bitiş işaretlerini birlikte kullanmak, modun ek bir tam-eşleşme katmanı kullanıp kullanmamasından bağımsız olarak amacı açık kılar:

| Desen | Seçtiği kayıt kimlikleri |
| --- | --- |
| `^minecraft:iron_ingot$` | Yalnızca vanilla demir külçesi |
| `^minecraft:.*_ingot$` | Minecraft namespace'inde `_ingot` ile biten item'lar |
| `^[a-z0-9_.-]+:.*_ingot$` | Bütün namespace'lerde `_ingot` ile biten item'lar |
| `^minecraft:(iron\|gold)_ingot$` | Vanilla demir veya altın külçesi |
| `^mekanism:.*$` | Mekanism namespace'indeki kimlikler |
| `^minecraft:(water\|lava)$` | Fluid regex alanında vanilla su veya lav |

Anlamlar: `^` başlangıç, `$` bitiş, `.` herhangi bir karakter, `*` önceki öğenin sıfır veya daha fazla tekrarı, `(...)` grup, `|` alternatif. Minecraft ID'leri küçük harfli olduğu için varsayılan büyük/küçük harf duyarlılığı normal kullanımda sorun oluşturmuyor.

Rust `regex` 1.12.2'nin standart arama API'si, `^`/`$` olmadan metnin bir bölümünü de eşleştirebilir. Motor look-around ve backreference özelliklerini desteklemez; desen uzunluğu ve aranan metin uzunluğu ile sınırlı arama karmaşıklığı hedefler. Java'nın sınırsız `Pattern` kullanımıyla doğrudan eşdeğer kabul edilmemeli. [Kullanılan motorun belgeleri](https://docs.rs/regex/1.12.2/regex/)

Örneğin, standart motor davranışında `ingot` metnin içinde bu sözcüğü arar; `^minecraft:.*_ingot$` ise mod ve son eki birlikte sınırlar. **Curvy'nin aramadan önce otomatik anchor ekleyip eklemediği native koddan çıkarılmadı.** Bu nedenle bu mod için anchorsız substring davranışı oyun içinde doğrulanmış gibi sunulmuyor.

## Deny ve miktar düzenleme

UI'da deny ile tür başına/toplam miktar düzenleme de bulunuyor. Regex tür seçimini, miktar ayarı ne kadarının tutulacağını ifade ediyor. Örneğin külçeleri seçen bir regex ile stok limiti farklı amaçlar taşıyor.

Karışık item örneği + regex + deny listelerinde kural önceliği, miktarların birden fazla eşleşmede nasıl birleştiği ve tamamen boş regex'in Curvy tarafından kabul edilip edilmediği bu incelemede kesinleştirilmedi. Bunlar mevcut kapalı native uygulamada; gerektiğinde ayrı oyun içi senaryolarla doğrulanmalı.

## Flowline'a alınırsa önerilen davranış

Flowline'da mevcut `FilterEntry.name` zaten bir regex alanı. `CompiledFilter` bunu Java `Pattern` ile büyük/küçük harf duyarsız derliyor ve item'ın `getHoverName()` veya fluid'in görünen adında `find()` ile arıyor. Giriş sınırı 128 karakter. **Bu alan Curvy'nin ID regex'iyle farklı bir özelliği ifade ediyor.** Örneğin oyun dili veya örs ile verilen ad, görünen-ad eşleşmesini etkileyebilir; kayıt ID'si değişmez.

Bu bir uygulama önerisi; regex henüz Flowline'a eklenmedi:

- Mevcut item/fluid/tag/mod kuralları yanında açıkça **ID regex** adlı bir kural türü.
- Sunucuda bir kez derleme ve önbellekleme; aktarım döngüsünde yeniden derlememe.
- Desen uzunluğu ve derleme boyutu sınırları; karmaşık geri izleme davranışı oluşturmayan motor.
- Hatalı deseni kaydetmeden önce anlaşılır hata ve eşleşen kayıt kimliklerinin önizlemesi.
- Tam ID veya substring davranışını açık bir seçenekle belirtme; deny ve miktar önceliğini belgeleyip test etme.

Özellikle çok sayıdaki mod item'ını ortak son ekten seçmek için faydalı. Forge tag varsa tag filtresi daha az yazım hatası içerir; regex ortak bir tag bulunmadığında daha esnek olur.
