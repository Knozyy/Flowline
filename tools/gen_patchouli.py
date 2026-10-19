#!/usr/bin/env python3
"""Generates the optional Patchouli guide book (data/flowline/patchouli_books/guide) in English and Turkish, plus the
recipe that crafts it. Pure standard library, like gen_resources.py. Run from anywhere: python3 tools/gen_patchouli.py"""
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "flowline")
BOOK = os.path.join(ROOT, "patchouli_books", "guide")
BR = "$(br)"
BR2 = "$(br2)"


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def text(t):
    return {"type": "patchouli:text", "text": t}


def crafting(recipe, t=None):
    page = {"type": "patchouli:crafting", "recipe": f"flowline:{recipe}"}
    if t:
        page["text"] = t
    return page


# (category id, icon, sortnum) in display order
CATEGORIES = [
    ("basics", "flowline:item_pipe"),
    ("sides", "flowline:wrench"),
    ("filters", "flowline:filter_upgrade"),
    ("tools", "flowline:knozy_upgrade"),
    ("curved", "flowline:fluid_pipe"),
    ("integration", "flowline:filter_card"),
]

# entry id -> (category, icon, sortnum); text lives in the per-language tables below
ENTRIES = {
    "pipes": ("basics", "flowline:item_pipe", 0),
    "first_network": ("basics", "flowline:wrench", 1),
    "dyes": ("basics", "minecraft:red_dye", 2),
    "modes": ("sides", "flowline:wrench", 0),
    "distribution": ("sides", "minecraft:comparator", 1),
    "pacing": ("sides", "minecraft:clock", 2),
    "rules": ("filters", "flowline:filter_upgrade", 0),
    "rule_parts": ("filters", "minecraft:name_tag", 1),
    "chemical_rules": ("filters", "flowline:chemical_pipe", 2),
    "upgrades": ("tools", "flowline:knozy_upgrade", 0),
    "cards": ("tools", "flowline:filter_card", 1),
    "build_for_me": ("curved", "flowline:item_pipe", 0),
    "curved_laying": ("curved", "flowline:fluid_pipe", 1),
    "curved_editing": ("curved", "flowline:energy_pipe", 2),
    "curved_ends": ("curved", "flowline:wrench", 3),
    "mods": ("integration", "minecraft:book", 0),
    "datapacks": ("integration", "minecraft:command_block", 1),
    "config": ("integration", "minecraft:redstone_torch", 2),
}

EN = {
    "book": {
        "name": "Flowline Guide",
        "landing": "Item, fluid, energy and chemical pipes with per-side control, filters and upgrades. Start with $(l:basics/first_network)your first network$().",
    },
    "categories": {
        "basics": ("Basics", "Pipe types, a first network and colours."),
        "sides": ("Sides and Modes", "Every side of a pipe is configured on its own."),
        "filters": ("Filters", "Allow and Block rules for items, fluids and chemicals."),
        "tools": ("Upgrades and Tools", "Speed, Stack, Filter and Knozy upgrades, the wrench and the Filter Card."),
        "curved": ("Curved Pipes", "The off hand: Build for me, or a curved line of the same pipes."),
        "integration": ("Integrations", "Other mods, data packs, KubeJS and the config."),
    },
    "entries": {
        "pipes": ("Pipe Types", [
            text("$(item)Item$(), $(item)Fluid$() and $(item)Energy$() pipes connect to pipes of the same type and to any block that exposes the matching capability." + BR2 + "The $(item)Universal Pipe$() moves items, fluids and energy at once; each side can switch its channels on and off."),
            crafting("item_pipe", "Four pipes per craft: iron and gold around redstone and what the pipe carries."),
            crafting("universal_pipe"),
            text("The $(item)Chemical Pipe$() needs Mekanism and moves gases, infuse types, pigments and slurries." + BR2 + "Every pipe is a glass duct: coloured rails on its corners and a small cage where it bends or branches. Pipes can be waterlogged. They draw no animations, so they cost nothing beyond their model."),
            crafting("chemical_pipe"),
        ]),
        "first_network": ("Your First Network", [
            text("Every side of a pipe starts as $(thing)Insert$(): it only gives to what is next to it." + BR2 + "Sneak and right-click the side facing your source chest, tank or generator with the $(item)Flowline Wrench$(). It becomes $(thing)Extract$() and pulls from that block."),
            crafting("wrench"),
            text("The wrench cycles a side: Insert, Extract, disconnected." + BR2 + "Pipes of one type that touch form a network. Extract sides pull, Insert sides receive, and everything in between is just pipe."),
        ]),
        "dyes": ("Colours", [
            text("Right-click a pipe with a dye: its rails and cages take the colour, the glass keeps the pipe's own. Pipes of $(thing)two different colours never connect$(); undyed pipes connect to every colour. Using the pipe's own colour again washes it off."),
        ]),
        "modes": ("Extract and Insert", [
            text("$(thing)Extract$() sides have a redstone mode, a distribution mode, redstone output, a filter, upgrades, $(thing)Keep ≥$() (leave at least this much in the source) and, on energy, an FE/t limit."),
            text("$(thing)Insert$() sides have a $(thing)priority$(), an insert filter, $(thing)Max ≤$() (keep at most this much in the target) and $(thing)Overflow$(): a target that only gets what the others could not take." + BR2 + "Right-click a side to open its screen, whatever you hold (like a chest); sneak to place a block against the pipe instead."),
        ]),
        "distribution": ("Distribution and Redstone", [
            text("Distribution: $(thing)Nearest$(), $(thing)Farthest$(), $(thing)Round robin$(), $(thing)Random$(), $(thing)Balanced$() (splits an operation evenly) and $(thing)Priority$() (highest insert priority first)."),
            text("Redstone: ignored, needs signal, needs no signal, or $(thing)Pulse$() (one operation per rising edge)." + BR2 + "Sneak and scroll with the wrench on a side to change its distribution, or its redstone mode with Ctrl."),
        ]),
        "pacing": ("Pacing", [
            text("An Extract side starts at 30 ticks between operations. Every operation that moves something makes it 2 ticks faster (down to 5); every one that moves nothing doubles the wait (up to 100)." + BR2 + "A side with no target sleeps and costs nothing until its network changes."),
            text("All numbers are in the server config. The side screen's header shows what the side is doing: Working, Asleep, Waiting, Stuck or Idle."),
        ]),
        "rules": ("Filter Rules", [
            text("Each side has 9 rules, plus 9 per Filter upgrade. A stack matching any $(thing)Block$() rule never passes. If there are $(thing)Allow$() rules it must match one of them. With only Block rules, everything else passes." + BR2 + "Click the Allow/Block chip on a rule to switch it."),
            text("Add a rule by shift-clicking an item in your inventory, by clicking the list with an item in hand, or with $(thing)+ Add filter$(). Right-click a rule to remove it, click it to open the rule editor."),
            crafting("filter_upgrade"),
        ]),
        "rule_parts": ("What a Rule Can Match", [
            text("One rule can combine, and every part you set must match:" + BR + "- an item or fluid id" + BR + "- tags (any or all)" + BR + "- data (NBT): contains or exact" + BR + "- a mod, like $(thing)@create$()"),
            text("- a name pattern (case-insensitive regular expression)" + BR + "- a durability range in percent" + BR + "- an amount: a regulator just for matching stacks" + BR2 + "The rule editor shows what the rule does and whether your sample item matches it."),
        ]),
        "chemical_rules": ("Chemical Filters", [
            text("Chemical Pipes filter too. A chemical rule matches a chemical by its $(thing)id$() (click a Mekanism tank in your inventory to fill it in), its $(thing)mod$() or a $(thing)name pattern$(). Tags, data and durability do not apply." + BR2 + "Allow and Block work as everywhere else, on Extract and Insert sides."),
        ]),
        "upgrades": ("Upgrades", [
            text("Six upgrade slots per side. $(item)Speed$() lowers the starting interval, $(item)Stack$() multiplies the amount per operation, $(item)Filter$() adds rules, and $(item)Knozy$() counts as all three." + BR2 + "Insert sides only take Filter upgrades. Everything drops when the pipe is broken."),
            crafting("speed_upgrade"),
            crafting("stack_upgrade"),
            crafting("filter_upgrade"),
            crafting("knozy_upgrade"),
        ]),
        "cards": ("Filter Card", [
            text("The $(item)Filter Card$() copies a side's filter rules: sneak and right-click to copy, right-click another side to paste, sneak and use in the air to clear." + BR2 + "Only the rules travel; mode, settings and upgrades stay where they are."),
            crafting("filter_card"),
        ]),
        "build_for_me": ("Build for Me", [
            text("Hold a pipe in your $(thing)off hand$(). Look at a block: the route back to you appears as ghost pipes with the number needed, around obstacles and with as few turns as possible. Right-click to lay it."),
            text("Each pipe is placed normally: protection mods apply, and survival uses up the off-hand stack. A pipe in the main hand always places a block pipe." + BR2 + "The mode key ($(k:flowline.build)$()) switches the off hand to $(thing)Curved$()."),
        ]),
        "curved_laying": ("Laying a Curve", [
            text("In $(thing)Curved$() mode, right-click to start a line: on a block face, a pipe face, an existing curved node or in the air. Right-click in the air to add points, and on a face or a node to finish. Sneak and right-click to finish in the air."),
            text("A ghost tube shows the curve; it turns red when it would be refused. The line costs pipes from the off hand, about one per block, and gives them back when broken." + BR2 + "Esc or the mode key drops a half-drawn line."),
        ]),
        "curved_editing": ("Editing Curves", [
            text("Hold left-click on a curved pipe to break it like a block; in survival its pipes drop. The middle button turns the grid: off, axial, outer or inner corners, outer or inner edges; sneak to turn it back. Hold Ctrl to place freely."),
            text("Hold $(k:flowline.options)$() and scroll for the option wheel: Remove, Branch, Extend, Move, Force joint and Settings. Let go to use it on the node or curve you look at." + BR2 + "Dye a curved pipe like a block pipe: other colours do not connect."),
        ]),
        "curved_ends": ("Ends and Networks", [
            text("A curved pipe is part of the network it touches. An end on a chest, tank or machine is a pipe side: the same screen, wrench, Filter Card and upgrades. One Extract side drives the whole network, curves included."),
            text("An end on a block pipe face just carries. An end on any other block only holds the line. Blocks put in a curve's way break it and drop its pipes; entities stand on curves."),
        ]),
        "mods": ("Other Mods", [
            text("$(thing)Mekanism$() 10.4 adds the Chemical Pipe." + BR + "$(thing)JEI$() and $(thing)EMI$(): how-to pages on Flowline's items, and drag an item or fluid onto a filter slot or into the rule editor." + BR + "$(thing)Jade$(), $(thing)The One Probe$() and $(thing)WTHIT$() show the looked-at side's mode, pacing, priority and regulator." + BR2 + "Wrenches tagged $(thing)forge:tools/wrench$() work on pipes."),
        ]),
        "datapacks": ("Data Packs and KubeJS", [
            text("One block tag, empty by default:" + BR + "$(thing)flowline:no_connect$(): pipes never connect to these blocks." + BR2 + "Recipes are ordinary data pack recipes."),
            text("KubeJS needs no special support. For example:" + BR2 + "ServerEvents.tags('block', e => {" + BR + "  e.add('flowline:no_connect', 'minecraft:chest')" + BR + "})"),
        ]),
        "config": ("Configuration", [
            text("The config is per world: <world>/serverconfig/flowline-server.toml. It holds the amounts per operation, Stack multipliers, filter slots, pacing, network size and the Build for me range." + BR2 + "In a single-player world it can be edited in game from Mods > Flowline > Config."),
        ]),
    },
}

TR = {
    "book": {
        "name": "Flowline Rehberi",
        "landing": "Her yanı ayrı ayarlanan, filtreli ve yükseltmeli eşya, sıvı, enerji ve kimyasal boruları. $(l:basics/first_network)İlk ağınızla$() başlayın.",
    },
    "categories": {
        "basics": ("Temeller", "Boru türleri, ilk ağ ve renkler."),
        "sides": ("Taraflar ve Modlar", "Borunun her tarafı ayrı ayarlanır."),
        "filters": ("Filtreler", "Eşya, sıvı ve kimyasallar için İzin ve Engel kuralları."),
        "tools": ("Yükseltmeler ve Aletler", "Hız, Yığın, Filtre ve Knozy yükseltmeleri, anahtar ve Filtre Kartı."),
        "curved": ("Eğriler", "Sol el: Benim için inşa et ya da aynı borulardan kıvrımlı bir hat."),
        "integration": ("Entegrasyonlar", "Diğer modlar, veri paketleri, KubeJS ve ayarlar."),
    },
    "entries": {
        "pipes": ("Boru Türleri", [
            text("$(item)Eşya$(), $(item)Sıvı$() ve $(item)Enerji$() boruları aynı türdeki borulara ve uygun yeteneği sunan her bloka bağlanır." + BR2 + "$(item)Evrensel Boru$() eşya, sıvı ve enerjiyi birlikte taşır; her taraf kanallarını açıp kapatabilir."),
            crafting("item_pipe", "Her üretimde dört boru: kırmızıtaşın ve borunun taşıdığı şeyin etrafında demir ve altın."),
            crafting("universal_pipe"),
            text("$(item)Kimyasal Boru$() Mekanism ister; gazları, infuse türlerini, pigmentleri ve çamurları taşır." + BR2 + "Her boru bir cam kanaldır: köşelerinde renkli raylar, döndüğü ya da dallandığı yerde küçük bir kafes. Borular suyla dolabilir. Animasyon çizmezler; modellerinden başka bir maliyetleri yoktur."),
            crafting("chemical_pipe"),
        ]),
        "first_network": ("İlk Ağınız", [
            text("Borunun her tarafı $(thing)Insert$() (Alıcı) olarak başlar: yalnızca yanındakine verir." + BR2 + "$(item)Flowline Anahtarı$() ile kaynak sandığınıza, tankınıza ya da jeneratörünüze bakan tarafa eğilerek sağ tıklayın. Taraf $(thing)Extract$() (Çekici) olur ve o bloktan çeker."),
            crafting("wrench"),
            text("Anahtar bir tarafı sırayla değiştirir: Insert, Extract, bağlantı kesik." + BR2 + "Birbirine değen aynı tür borular bir ağ oluşturur. Extract tarafları çeker, Insert taraflar alır, aradaki her şey sadece borudur."),
        ]),
        "dyes": ("Renkler", [
            text("Bir boruya boyayla sağ tıklayın: rayları ve kafesleri o rengi alır, cam borunun kendi rengini korur. $(thing)İki farklı renkteki borular asla bağlanmaz$(); boyasız borular her renge bağlanır. Borunun kendi rengini tekrar kullanmak boyayı siler."),
        ]),
        "modes": ("Extract ve Insert", [
            text("$(thing)Extract$() taraflarının redstone modu, dağıtım modu, redstone çıktısı, filtresi, yükseltmeleri, $(thing)Keep ≥$() (kaynakta en az bu kadar bırak) ve enerjide FE/t sınırı vardır."),
            text("$(thing)Insert$() taraflarının $(thing)önceliği$(), bir alıcı filtresi, $(thing)Max ≤$() (hedefte en fazla bu kadar tut) ve $(thing)Overflow$() (taşma) vardır: yalnızca diğerlerinin alamadığını alan hedef." + BR2 + "Ekranını açmak için bir tarafa sağ tıklayın; elinizde ne olduğu önemli değil (sandık gibi). Boruya blok koymak için eğilerek tıklayın."),
        ]),
        "distribution": ("Dağıtım ve Redstone", [
            text("Dağıtım: $(thing)En yakın$(), $(thing)En uzak$(), $(thing)Sırayla$(), $(thing)Rastgele$(), $(thing)Dengeli$() (bir işlemi eşit böler) ve $(thing)Öncelik$() (en yüksek öncelik önce)."),
            text("Redstone: yok say, sinyal ister, sinyal istemez ya da $(thing)Darbe$() (yükselen kenar başına bir işlem)." + BR2 + "Dağıtımı değiştirmek için anahtarla eğilip tekerleği çevirin; Ctrl ile redstone modunu."),
        ]),
        "pacing": ("Tempo", [
            text("Extract tarafı işlemler arasında 30 tick ile başlar. Bir şey taşıyan her işlem 2 tick hızlandırır (5'e kadar); hiçbir şey taşımayan her işlem bekleyişi ikiye katlar (100'e kadar)." + BR2 + "Hedefi olmayan taraf uyur ve ağı değişene kadar hiçbir maliyeti olmaz."),
            text("Tüm sayılar sunucu ayarlarındadır. Taraf ekranının başlığı tarafnın ne yaptığını gösterir: Çalışıyor, Uyuyor, Bekliyor, Takıldı ya da Boşta."),
        ]),
        "rules": ("Filtre Kuralları", [
            text("Her tarafta 9 kural vardır, her Filtre yükseltmesi 9 tane daha ekler. Herhangi bir $(thing)Engel$() kuralına uyan yığın asla geçmez. $(thing)İzin$() kuralları varsa bunlardan birine uymalıdır. Yalnızca Engel kuralları varsa geri kalan her şey geçer." + BR2 + "Değiştirmek için kuraldaki İzin/Engel çipine tıklayın."),
            text("Envanterinizdeki bir eşyaya shift ile tıklayarak, elinizde eşyayla listeye tıklayarak ya da $(thing)+ Add filter$() ile kural ekleyin. Kuralı silmek için sağ tıklayın, düzenleyiciyi açmak için tıklayın."),
            crafting("filter_upgrade"),
        ]),
        "rule_parts": ("Bir Kural Neyi Eşleyebilir", [
            text("Bir kural şunları birleştirebilir; belirlediğiniz her parça uymalıdır:" + BR + "- bir eşya ya da sıvı id'si" + BR + "- etiketler (herhangi biri ya da hepsi)" + BR + "- veri (NBT): içerir ya da tam eşit" + BR + "- bir mod, örneğin $(thing)@create$()"),
            text("- bir isim deseni (büyük/küçük harfe duyarsız düzenli ifade)" + BR + "- yüzde olarak dayanıklılık aralığı" + BR + "- bir miktar: yalnızca eşleşen yığınlar için regülatör" + BR2 + "Kural düzenleyici kuralın ne yaptığını ve örnek eşyanızın uyup uymadığını gösterir."),
        ]),
        "chemical_rules": ("Kimyasal Filtreler", [
            text("Kimyasal Borular da filtreler. Bir kimyasal kuralı kimyasalı $(thing)id$()'siyle (envanterinizdeki bir Mekanism tankına tıklayıp doldurabilirsiniz), $(thing)modu$()yla ya da bir $(thing)isim deseni$()yle eşler. Etiket, veri ve dayanıklılık geçerli değildir." + BR2 + "İzin ve Engel her yerdeki gibi, Extract ve Insert taraflarında çalışır."),
        ]),
        "upgrades": ("Yükseltmeler", [
            text("Her tarafta altı yükseltme yuvası. $(item)Hız$() başlangıç aralığını azaltır, $(item)Yığın$() işlem başına miktarı çarpar, $(item)Filtre$() kural ekler, $(item)Knozy$() üçü yerine sayılır." + BR2 + "Insert tarafları yalnızca Filtre yükseltmesi alır. Boru kırılınca her şey düşer."),
            crafting("speed_upgrade"),
            crafting("stack_upgrade"),
            crafting("filter_upgrade"),
            crafting("knozy_upgrade"),
        ]),
        "cards": ("Filtre Kartı", [
            text("$(item)Filtre Kartı$() bir tarafın filtre kurallarını kopyalar: kopyalamak için eğilip sağ tıklayın, yapıştırmak için başka bir tarafa sağ tıklayın, temizlemek için eğilip havada kullanın." + BR2 + "Yalnızca kurallar taşınır; mod, ayarlar ve yükseltmeler yerinde kalır."),
            crafting("filter_card"),
        ]),
        "build_for_me": ("Benim İçin İnşa Et", [
            text("$(thing)Sol elinizde$() bir boru tutun. Bir bloka bakın: size geri dönen yol, gereken sayıyla birlikte hayalet borular olarak, engellerin etrafından ve en az dönüşle görünür. Döşemek için sağ tıklayın."),
            text("Her boru normal yerleştirilir: koruma modları geçerlidir, hayatta kalma modunda sol eldeki yığın tüketilir. Ana eldeki boru her zaman blok boru yerleştirir." + BR2 + "Mod tuşu ($(k:flowline.build)$()) sol eli $(thing)Kıvrımlı$() moda geçirir."),
        ]),
        "curved_laying": ("Kıvrım Döşemek", [
            text("$(thing)Kıvrımlı$() modda hattı başlatmak için sağ tıklayın: bir blok yüzünde, bir boru yüzünde, var olan bir kıvrım düğümünde ya da havada. Havada sağ tık nokta ekler; bir yüzde ya da düğümde bitirir. Havada bitirmek için eğilip sağ tıklayın."),
            text("Hayalet bir tüp kıvrımı gösterir; reddedilecekse kırmızı olur. Hat sol eldeki borulardan, yaklaşık blok başına bir tane harcar; kırılınca geri verir." + BR2 + "Esc ya da mod tuşu yarım hattı bırakır."),
        ]),
        "curved_editing": ("Kıvrımları Düzenlemek", [
            text("Kıvrımlı boruyu blok gibi kırmak için sol tıkı basılı tutun; survival'da boruları yere düşer. Orta tuş ızgarayı değiştirir: kapalı, eksenler, dış ya da iç köşeler, dış ya da iç kenarlar; geri çevirmek için eğilin. Serbest yerleştirmek için Ctrl'yi basılı tutun."),
            text("Seçenek çarkı için $(k:flowline.options)$() basılı tutup tekerleği çevirin: Sil, Dallandır, Uzat, Taşı, Birleşme yap, Ayarlar. Bıraktığınızda baktığınız düğüme ya da kıvrıma uygulanır." + BR2 + "Kıvrımlı boru blok boru gibi boyanır: farklı renkler bağlanmaz."),
        ]),
        "curved_ends": ("Uçlar ve Ağlar", [
            text("Kıvrımlı boru değdiği ağın parçasıdır. Sandığa, tanka ya da makineye bağlı uç bir boru tarafıdır: aynı ekran, anahtar, Filtre Kartı ve yükseltmeler. Tek bir Extract tarafı, kıvrımlar dahil bütün ağı çalıştırır."),
            text("Blok borusu yüzündeki uç yalnızca taşır. Başka bir bloktaki uç yalnızca hattı tutar. Kıvrımın yoluna konan blok onu kırar ve borularını düşürür; varlıklar kıvrımların üstünde durur."),
        ]),
        "mods": ("Diğer Modlar", [
            text("$(thing)Mekanism$() 10.4 Kimyasal Boruyu ekler." + BR + "$(thing)JEI$() ve $(thing)EMI$(): Flowline eşyalarında kısa kullanım sayfaları; bir eşyayı ya da sıvıyı filtre yuvasına ya da kural düzenleyiciye sürükleyin." + BR + "$(thing)Jade$(), $(thing)The One Probe$() ve $(thing)WTHIT$() bakılan tarafın modunu, temposunu, önceliğini ve regülatörünü gösterir." + BR2 + "$(thing)forge:tools/wrench$() etiketli anahtarlar borularda çalışır."),
        ]),
        "datapacks": ("Veri Paketleri ve KubeJS", [
            text("Varsayılan olarak boş bir blok etiketi:" + BR + "$(thing)flowline:no_connect$(): borular bu bloklara asla bağlanmaz." + BR2 + "Tarifler sıradan veri paketi tarifleridir."),
            text("KubeJS özel destek gerektirmez. Örneğin:" + BR2 + "ServerEvents.tags('block', e => {" + BR + "  e.add('flowline:no_connect', 'minecraft:chest')" + BR + "})"),
        ]),
        "config": ("Ayarlar", [
            text("Ayar dünya başınadır: <dünya>/serverconfig/flowline-server.toml. İşlem başına miktarları, Yığın çarpanlarını, filtre yuvalarını, tempoyu, ağ boyutunu ve Benim için inşa et menzilini içerir." + BR2 + "Tek oyunculu dünyada oyun içinden Mods > Flowline > Config ile düzenlenebilir."),
        ]),
    },
}


def main():
    shutil.rmtree(BOOK, ignore_errors=True)
    write(os.path.join(BOOK, "book.json"), {
        "name": EN["book"]["name"],
        "landing_text": EN["book"]["landing"],
        "version": 1,
        "show_progress": False,
    })
    for lang, data in (("en_us", EN), ("tr_tr", TR)):
        base = os.path.join(BOOK, lang)
        for index, (cat, icon) in enumerate(CATEGORIES):
            name, desc = data["categories"][cat]
            write(os.path.join(base, "categories", f"{cat}.json"),
                  {"name": name, "description": desc, "icon": icon, "sortnum": index})
        for entry, (cat, icon, sort) in ENTRIES.items():
            title, pages = data["entries"][entry]
            write(os.path.join(base, "entries", cat, f"{entry}.json"),
                  {"name": title, "category": f"flowline:{cat}", "icon": icon, "sortnum": sort, "pages": pages})
    # book.json is not localised (Patchouli only translates it through lang keys); categories and entries are.
    # The book is crafted from a book and an item pipe, and only when Patchouli is installed.
    write(os.path.join(ROOT, "recipes", "guide_book.json"), {
        "conditions": [{"type": "forge:mod_loaded", "modid": "patchouli"}],
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "flowline:item_pipe"}],
        "result": {"item": "patchouli:guide_book", "nbt": {"patchouli:book": "flowline:guide"}},
    })
    print("book", EN["book"]["name"], "/", TR["book"]["name"])


if __name__ == "__main__":
    main()
