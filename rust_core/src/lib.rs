use jni::objects::{JByteArray, JClass, JString};
use jni::sys::jint;
use jni::JNIEnv;
use zxingcpp::{ImageFormat, ImageView};

#[no_mangle]
pub extern "system" fn Java_com_example_scanner_ScannerBridge_decodeFrame<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    y_buffer: JByteArray<'local>,
    width: jint,
    height: jint,
) -> JString<'local> {
    let bytes = match env.convert_byte_array(&y_buffer) {
        Ok(b) => b,
        Err(_) => return env.new_string("").unwrap(),
    };

    let w = width as u32;
    let h = height as u32;

    // Görüntüyü kopyalamadan doğrudan zxing-cpp ImageView olarak bağla
    let Ok(image_view) = ImageView::from_slice(&bytes, w, h, ImageFormat::Lum) else {
        return env.new_string("").unwrap();
    };

    let reader = zxingcpp::read()
        .try_rotate(true)
        .try_invert(true)
        .try_harder(true)
        .try_downscale(true);

    if let Ok(barcodes) = reader.from(image_view) {
        if let Some(barcode) = barcodes.first() {
            let fmt_raw = format!("{:?}", barcode.format()).to_uppercase();
            // Sadece Code128, Code39 gibi formatları CODE_128 yap (QRCode'u bozma)
            let fmt = if fmt_raw.starts_with("CODE") {
                fmt_raw.replacen("CODE", "CODE_", 1)
            } else if fmt_raw == "QRCODE" {
                "QR_CODE".to_string()
            } else {
                fmt_raw
            };

            let out = format!("{}|{}", fmt, barcode.text());
            return env.new_string(out).unwrap();
        }
    }

    env.new_string("").unwrap()
}