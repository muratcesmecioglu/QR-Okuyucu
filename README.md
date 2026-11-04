# ⚡ Hızlı Kod Okuyucu (Rust + Android)

![Rust](https://img.shields.io/badge/Engine-Rust-orange?style=for-the-badge&logo=rust)
![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![Ad-Free](https://img.shields.io/badge/100%25-Reklamsız%20%26%20Sınırsız-brightgreen?style=for-the-badge)
![Privacy](https://img.shields.io/badge/Gizlilik-İnternet%20İzni%20Yok-blue?style=for-the-badge)

**Hızlı Kod Okuyucu**, görüntü işleme ve kod çözme çekirdeği **Rust** diliyle yazılmış, Android üzerinde sıfır gecikmeyle çalışan ultra hafif, sade ve modern bir QR / Barkod okuyucudur.

Piyasadaki abonelik dayatan, tam ekran reklamlarla bekleten ve verilerinizi sunuculara gönderen uygulamalara karşı; **tamamen reklamsız, sınırsız ve %100 çevrimdışı** olarak tasarlanmıştır.

---

## 🛡️ Neden Bu Uygulama?

* **🚫 %100 Reklamsız ve Sınırsız:** Uygulama içi satın alma, tarama limiti, abonelik veya reklam banner'ı yoktur.
* **🔒 Tam Gizlilik (`INTERNET` İzni Bile Yoktur):** Uygulama Android'den internet erişim izni dahi istemez. Okuttuğunuz şifreler, linkler ve kişisel kodlar cihazınızdan dışarı **asla** çıkmaz.
* **🦀 Rust Gücüyle Anında Okuma:** Kamera kareleri JNI üzerinden doğrudan Rust (`rxing`) motoruna aktarılır. Sadece vizör karesinin içi kırpılarak (crop) işlendiği için milisaniyeler içinde sonuç verir.

---

## ✨ Öne Çıkan Özellikler

### 1. 🚀 Hızlı Ayarlar Karosu (Quick Settings Tile) ve Kilit Ekranı Desteği
* Android'in üst bildirim panelini aşağı çektiğinizde gelen **Hızlı Ayarlar** bölümüne kendi kısayol ikonunu ekler.
* **Kilit ekranı üzerinde çalışır:** Telefonunuzun kilidini açmanıza bile gerek kalmadan üst panelden tek dokunuşla kamerayı açıp kod okutabilirsiniz.

### 2. 🎯 Odaklı Vizör (%80 Kare Kırpma)
* Ekranın ortasındaki genişletilmiş (%80) karenin dışı karartılmıştır ve **sadece karenin içindeki alan** taranır.
* Masada veya rafta yan yana duran birden fazla barkodun yanlışlıkla okunmasını engeller.

### 3. 🧠 Akıllı Aksiyonlar ve Onay Sistemi
Okunan verinin türünü otomatik olarak tanır ve uygun seçenekleri sunar:
* **🔗 Bağlantılar (URL):** *"Bu bağlantı açılsın mı?"* diye sorar; tek tıkla tarayıcıda açabilir veya kopyalayabilirsiniz.
* **📶 Wi-Fi Kodları:** Ağ adını (SSID) ve şifresini ekranda açıkça gösterir. **"Ağa Bağlan"** dediğinizde şifreyi otomatik kopyalar ve Android'in yerel Wi-Fi bağlanma ekranını tetikler.
* **🛒 Ürün Barkodları (EAN / UPC / Code 128):** Ürünü tek tıkla **Google'da aratabilir** veya barkod numarasını kopyalayabilirsiniz.
* **📝 Düz Metinler:** Anında panoya kopyalama veya diğer uygulamalarla paylaşma imkânı sunar.

### 4. 🕒 Son 3 Okuma Geçmişi
* Ekranın alt kısmındaki şık panelde son okuttuğunuz 3 kod (`🔗`, `📶`, `📝` ikonlarıyla) listelenir.
* Geçmişteki bir kayda dokunduğunuzda:
  * **Link ise:** Açmak mı yoksa kopyalamak mı istediğinizi sorar.
  * **Wi-Fi ise:** Ağa bağlanma penceresini açar.
  * **Metin ise:** Hiçbir soru sormadan **doğrudan panoya kopyalar**.

### 5. 🖼️ Ekran Görüntüsünden / Galeriden Okuma (Paylaşarak Tara)
* WhatsApp'tan gelen veya bir web sitesinde gördüğünüz QR kodu okutmak için ikinci bir telefona ihtiyacınız yok.
* Görseli veya ekran görüntüsünü Android'in **"Paylaş"** menüsünden **Hızlı Kod Okuyucu**'ya gönderin; kamera açılmadan görseldeki kod anında çözülür.

### 6. 🔦 Ergonomik Tek El Kontrolleri
* **Ses Tuşlarıyla Flaş Kontrolü:** Karanlık ortamlarda ekranda buton aramak yerine telefonun **Ses Açma / Kısma** tuşlarına basarak flaşı (feneri) anında açıp kapatabilirsiniz.
* **Çift Dokunuşla Hızlı Zoom (2.5x):** Uzaktaki veya çok küçük kodları okumak için ekrana çift dokunarak anında 2.5 kat yakınlaştırma yapabilirsiniz.

### 7. 🔄 Ters (Negatif) ve Dikey Barkod Desteği
* Karanlık mod tasarımlarında kullanılan **siyah zemin üzerine beyaz (negatif) QR kodları** otomatik çözer.
* Telefonu dik tutarken yatay market barkodlarını (1D) okuyabilmek için Rust katmanında görüntüyü otomatik olarak **90 derece döndürerek** tarar.

---

## 📊 Desteklenen Kod Formatları

| 2B Matris Kodlar | 1B Çizgisel Barkodlar |
| :--- | :--- |
| ✅ **QR Code** (Standart & Ters/Negatif) | ✅ **EAN-13 / EAN-8** (Market & Ürün Barkodları) |
| ✅ **Data Matrix** (İlaç & Sanayi Karekodları) | ✅ **Code 128** (Kargo & Lojistik) |
| ✅ **Aztec** (Ulaşım & Bilet Kodları) | ✅ **Code 39** |
| ✅ **PDF417** (Kimlik & Havayolu Biniş Kartları) | ✅ **UPC-A / UPC-E** |

---

## ⚙️ Kişiselleştirilebilir Ayarlar

Kamera ekranının sağ üst köşesindeki **⚙️ Ayarlar** butonundan kullanım alışkanlığınıza göre şu özellikleri değiştirebilirsiniz:

1. **Okuma sonrası otomatik kopyala (Açık / Kapalı):** Açık olduğunda, okunan her kodu (veya Wi-Fi şifresini) size sormadan anında panoya kopyalar.
2. **Okuma sonrası kapansın (Açık / Kapalı):** Açık olduğunda *"Tara ve Çık"* modunda çalışır; kodu okur, kopyalar ve hiçbir pencere göstermeden uygulamayı anında kapatır.
3. **Okumada titreşim (Açık / Kapalı):** Kod başarıyla okunduğunda 100ms'lik net bir motor titreşimiyle fiziksel geri bildirim verir.

---

## 🛠️ Kurulum ve APK İndirme

### Yöntem 1: Hazır APK İndirme (GitHub Actions)
Bilgisayarınıza hiçbir program kurmadan doğrudan derlenmiş APK'yı indirmek için:
1. Bu reponun üst menüsündeki **Actions** sekmesine tıklayın.
2. En son tamamlanan yeşil onaylı (**Build APK**) iş akışına girin.
3. Sayfanın altındaki **Artifacts** bölümünden **`Hizli-Kod-Okuyucu-APK`** dosyasını indirip telefonunuza kurun.

### Yöntem 2: Hızlı Ayarlar Karosunu Ekleme
Uygulamayı kurduktan sonra:
1. Telefonunuzun üst bildirim panelini iki kez aşağı çekin.
2. **Düzenle (Kalem ✏️)** ikonuna dokunun.
3. Listeden **"Kod Tara"** butonunu bulup üstteki aktif karolarınızın arasına sürükleyin.

---

## 🏗️ Proje Mimarisi

```text
├── .github/workflows/build-apk.yml   # Otomatik Rust + Android APK CI/CD hattı
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml       # İzinler, TileService ve Share Receiver tanımları
│       └── java/com/example/scanner/
│           ├── MainActivity.kt       # CameraX, Vizör kırpma, Geçmiş ve Akıllı Diyaloglar
│           ├── SettingsActivity.kt   # Kullanıcı tercihleri ekranı
│           ├── ScannerBridge.kt      # Rust JNI bağlantısı
│           └── ScannerTileService.kt # Android Hızlı Ayarlar Karosu servisi
└── rust_core/
    ├── Cargo.toml
    └── src/lib.rs                    # Normal, Negatif ve 90° döndürülmüş YUV çözücü motor
```

---

## 📄 Lisans

Bu proje tamamen açık kaynaklıdır. Dilediğiniz gibi kullanabilir, çatallayabilir (fork) ve geliştirebilirsiniz.

---

## 📄 Değişim Günlüğü

v1.1
- Otomatik kapanma seçeneğinde link okutursanız panoya kopyalayıp çıkıyordu. Artık linki otomatik açıp programı kapatabiliyor. 

v1
- İlk sürüm
