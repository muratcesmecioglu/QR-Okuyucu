use jni::objects::{JByteArray, JClass, JString};
use jni::sys::jint;
use jni::JNIEnv;
use rxing::{
    common::HybridBinarizer, BarcodeFormat, BinaryBitmap, DecodeHints,
    LuminanceSource, MultiFormatReader, PlanarYUVLuminanceSource,
};

#[no_mangle]
pub extern "system" fn Java_com_example_scanner_ScannerBridge_decodeFrame<'local>(
    mut env: JNIEnv<'local>,
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

    let source = PlanarYUVLuminanceSource::new(u8_bytes, w, h, 0, 0, w, h, false);

    let mut hints = DecodeHints::default();
    hints.PossibleFormats = Some(vec![
        BarcodeFormat::QR_CODE,
        BarcodeFormat::DATA_MATRIX,
        BarcodeFormat::AZTEC,
        BarcodeFormat::EAN_13,
        BarcodeFormat::EAN_8,
        BarcodeFormat::CODE_128,
        BarcodeFormat::CODE_39,
        BarcodeFormat::PDF_417,
    ]);
    hints.TryHarder = Some(true);

    let mut reader = MultiFormatReader::default();

    let binarizer = HybridBinarizer::new(source.clone());
    let mut bitmap = BinaryBitmap::new(binarizer);
    if let Ok(result) = reader.decode_with_hints(&mut bitmap, &hints) {
        let out = format!("{:?}|{}", result.getBarcodeFormat(), result.getText());
        return env.new_string(out).unwrap();
    }

    if let Ok(inverted_source) = source.invert() {
        let inv_binarizer = HybridBinarizer::new(inverted_source);
        let mut inv_bitmap = BinaryBitmap::new(inv_binarizer);
        if let Ok(result) = reader.decode_with_hints(&mut inv_bitmap, &hints) {
            let out = format!("{:?}|{}", result.getBarcodeFormat(), result.getText());
            return env.new_string(out).unwrap();
        }
    }

    env.new_string("").unwrap()
}