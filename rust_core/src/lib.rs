use jni::objects::{JByteArray, JClass, JString};
use jni::sys::jint;
use jni::JNIEnv;
use rxing::{
    common::HybridBinarizer, BinaryBitmap, MultiFormatReader,
    PlanarYUVLuminanceSource, Reader,
};
use std::rc::Rc;

fn try_decode(data: Vec<u8>, w: usize, h: usize, inverted: bool) -> Option<String> {
    let mut reader = MultiFormatReader::default();
    let source = PlanarYUVLuminanceSource::new_with_all(
        data, w, h, 0, 0, w, h, false, inverted,
    ).ok()?;
    let binarizer = HybridBinarizer::new(Box::new(source));
    let mut bitmap = BinaryBitmap::new(Rc::new(binarizer));
    let result = reader.decode(&mut bitmap).ok()?;
    Some(format!("{:?}|{}", result.getBarcodeFormat(), result.getText()))
}

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

    if u8_bytes.len() < w * h {
        return env.new_string("").unwrap();
    }

    // 1. Normal Tarama
    if let Some(res) = try_decode(u8_bytes.clone(), w, h, false) {
        return env.new_string(res).unwrap();
    }

    // 2. Ters (Negatif) Tarama
    if let Some(res) = try_decode(u8_bytes.clone(), w, h, true) {
        return env.new_string(res).unwrap();
    }

    // 3. 90 Derece Döndürülmüş Tarama (Dik tutulan 1D barkodlar için)
    let mut rotated = vec![0u8; w * h];
    for y in 0..h {
        for x in 0..w {
            rotated[x * h + (h - 1 - y)] = u8_bytes[y * w + x];
        }
    }
    if let Some(res) = try_decode(rotated, h, w, false) {
        return env.new_string(res).unwrap();
    }

    env.new_string("").unwrap()
}