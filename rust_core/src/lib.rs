use jni::objects::{JByteArray, JClass, JString};
use jni::sys::jint;
use jni::JNIEnv;
use rxing::{
    common::HybridBinarizer, BinaryBitmap, MultiFormatReader,
    PlanarYUVLuminanceSource, Reader,
};
use std::rc::Rc;

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

    let u8_bytes: Vec<u8> = bytes.into_iter().map(|b| b as u8).collect();
    let w = width as usize;
    let h = height as usize;

    let mut reader = MultiFormatReader::default();

    // 1. Normal Tarama (QR, DataMatrix, Barkod vb.)
    if let Ok(source) = PlanarYUVLuminanceSource::new_with_all(
        u8_bytes.clone(),
        w,
        h,
        0,
        0,
        w,
        h,
        false,
        false,
    ) {
        let binarizer = HybridBinarizer::new(Box::new(source));
        let mut bitmap = BinaryBitmap::new(Rc::new(binarizer));
        if let Ok(result) = reader.decode(&mut bitmap) {
            let out = format!("{:?}|{}", result.getBarcodeFormat(), result.getText());
            return env.new_string(out).unwrap();
        }
    }

    // 2. Ters (Negatif / Siyah zemin üstü beyaz) Tarama
    if let Ok(inv_source) = PlanarYUVLuminanceSource::new_with_all(
        u8_bytes,
        w,
        h,
        0,
        0,
        w,
        h,
        false,
        true,
    ) {
        let inv_binarizer = HybridBinarizer::new(Box::new(inv_source));
        let mut inv_bitmap = BinaryBitmap::new(Rc::new(inv_binarizer));
        if let Ok(result) = reader.decode(&mut inv_bitmap) {
            let out = format!("{:?}|{}", result.getBarcodeFormat(), result.getText());
            return env.new_string(out).unwrap();
        }
    }

    env.new_string("").unwrap()
}
