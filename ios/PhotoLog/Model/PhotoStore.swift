import UIKit
import ImageIO
import UniformTypeIdentifiers
import CoreLocation
import Photos

struct PhotoInfo: Sendable {
    var date: String
    var lat: Double?
    var lon: Double?
}

/**
 * 사진 파일을 앱 전용 저장 공간에 저장하고 읽는다.
 * 사진 앱·파일 앱에서는 보이지 않는다. 앱을 지우면 함께 지워지므로 백업(zip)으로 보관·이전한다.
 */
enum PhotoStore {
    static func url(_ file: String) -> URL { Paths.photos.appendingPathComponent(file) }

    /// 앱 전용 공간에 새 사진 파일 자리를 만든다(이름이 겹치지 않게).
    static func newFile(ext: String = "jpg", preferred: String? = nil) -> URL {
        let fm = FileManager.default
        if let preferred = preferred {
            let u = Paths.photos.appendingPathComponent(preferred)
            if !fm.fileExists(atPath: u.path) { return u }
        }
        let stamp = Dates.format(Date(), "yyyyMMdd_HHmmss_SSS")
        var u = Paths.photos.appendingPathComponent("PHOTOLOG_\(stamp).\(ext)")
        var n = 1
        while fm.fileExists(atPath: u.path) {
            u = Paths.photos.appendingPathComponent("PHOTOLOG_\(stamp)_\(n).\(ext)")
            n += 1
        }
        return u
    }

    static func delete(_ file: String) {
        guard !file.isEmpty else { return }
        try? FileManager.default.removeItem(at: url(file))
    }

    // MARK: - 날짜·위치(EXIF) 읽기

    struct Meta {
        var date: String?
        var lat: Double?
        var lon: Double?
    }

    static func readMeta(_ src: CGImageSource) -> Meta {
        guard let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any] else { return Meta() }
        let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any]
        let tiff = props[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
        let gps = props[kCGImagePropertyGPSDictionary] as? [CFString: Any]

        var meta = Meta()
        let dt = (exif?[kCGImagePropertyExifDateTimeOriginal] as? String)
            ?? (tiff?[kCGImagePropertyTIFFDateTime] as? String)
        if let dt = dt, dt.count >= 10, !dt.hasPrefix("0000") {
            meta.date = String(dt.prefix(10)).replacingOccurrences(of: ":", with: "-")
        }
        if let gps = gps,
           let la = (gps[kCGImagePropertyGPSLatitude] as? NSNumber)?.doubleValue,
           let lo = (gps[kCGImagePropertyGPSLongitude] as? NSNumber)?.doubleValue,
           !(la == 0 && lo == 0) {
            meta.lat = (gps[kCGImagePropertyGPSLatitudeRef] as? String) == "S" ? -la : la
            meta.lon = (gps[kCGImagePropertyGPSLongitudeRef] as? String) == "W" ? -lo : lo
        }
        return meta
    }

    // MARK: - 가져오기 · 촬영

    /// 사진 앱에서 고른 사진을 원본 그대로(EXIF 포함) 앱 전용 공간에 복사한다.
    /// 사진에 위치·날짜가 없으면 사진 보관함의 촬영 정보로 보완한다.
    static func importData(_ data: Data, assetDate: Date?, assetLocation: CLLocation?) -> (String, PhotoInfo)? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil), CGImageSourceGetCount(src) > 0 else { return nil }
        let meta = readMeta(src)
        var ext = "jpg"
        if let t = CGImageSourceGetType(src), let e = UTType(t as String)?.preferredFilenameExtension {
            ext = e
        }
        let dst = newFile(ext: ext)
        do {
            try data.write(to: dst, options: .atomic)
        } catch {
            return nil
        }
        var info = PhotoInfo(
            date: meta.date ?? assetDate.map { Dates.format($0, "yyyy-MM-dd") } ?? Dates.today(),
            lat: meta.lat,
            lon: meta.lon
        )
        if info.lat == nil, let l = assetLocation {
            info.lat = l.coordinate.latitude
            info.lon = l.coordinate.longitude
        }
        return (dst.lastPathComponent, info)
    }

    /// 사진 보관함의 촬영 날짜·위치(권한이 있을 때만).
    static func assetInfo(_ id: String?) -> (Date?, CLLocation?) {
        guard let id = id else { return (nil, nil) }
        let st = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard st == .authorized || st == .limited else { return (nil, nil) }
        guard let a = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else { return (nil, nil) }
        return (a.creationDate, a.location)
    }

    static func requestLibraryAccess() {
        if PHPhotoLibrary.authorizationStatus(for: .readWrite) == .notDetermined {
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { _ in }
        }
    }

    /// 카메라로 찍은 사진을 JPEG로 저장한다. 카메라는 위치를 넣지 않으므로 방금 전 폰 위치(15분 이내)를 사진에 기록한다.
    static func saveCapture(image: UIImage, metadata: [String: Any], location: CLLocation?) -> (String, PhotoInfo)? {
        guard let cg = image.cgImage else { return nil }
        var meta = metadata
        let orientation = exifOrientation(image.imageOrientation)
        meta[kCGImagePropertyOrientation as String] = NSNumber(value: orientation)
        if var tiff = meta[kCGImagePropertyTIFFDictionary as String] as? [String: Any] {
            tiff[kCGImagePropertyTIFFOrientation as String] = NSNumber(value: orientation)
            meta[kCGImagePropertyTIFFDictionary as String] = tiff
        }
        var exif = meta[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        if exif[kCGImagePropertyExifDateTimeOriginal as String] == nil {
            exif[kCGImagePropertyExifDateTimeOriginal as String] = Dates.format(Date(), "yyyy:MM:dd HH:mm:ss")
        }
        meta[kCGImagePropertyExifDictionary as String] = exif
        if let loc = location, meta[kCGImagePropertyGPSDictionary as String] == nil {
            meta[kCGImagePropertyGPSDictionary as String] = gpsDict(loc)
        }
        meta[kCGImageDestinationLossyCompressionQuality as String] = 0.92

        let dst = newFile(ext: "jpg")
        guard let d = CGImageDestinationCreateWithURL(dst as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(d, cg, meta as CFDictionary)
        guard CGImageDestinationFinalize(d) else {
            try? FileManager.default.removeItem(at: dst)
            return nil
        }

        var date = Dates.today()
        if let src = CGImageSourceCreateWithURL(dst as CFURL, nil), let dt = readMeta(src).date { date = dt }
        let info = PhotoInfo(date: date, lat: location?.coordinate.latitude, lon: location?.coordinate.longitude)
        return (dst.lastPathComponent, info)
    }

    private static func gpsDict(_ l: CLLocation) -> [String: Any] {
        let c = l.coordinate
        var d: [String: Any] = [
            kCGImagePropertyGPSLatitude as String: abs(c.latitude),
            kCGImagePropertyGPSLatitudeRef as String: c.latitude >= 0 ? "N" : "S",
            kCGImagePropertyGPSLongitude as String: abs(c.longitude),
            kCGImagePropertyGPSLongitudeRef as String: c.longitude >= 0 ? "E" : "W",
            kCGImagePropertyGPSHPositioningError as String: l.horizontalAccuracy,
        ]
        if l.verticalAccuracy >= 0 {
            d[kCGImagePropertyGPSAltitude as String] = abs(l.altitude)
            d[kCGImagePropertyGPSAltitudeRef as String] = l.altitude < 0 ? 1 : 0
        }
        return d
    }

    private static func exifOrientation(_ o: UIImage.Orientation) -> UInt32 {
        switch o {
        case .up: return 1
        case .upMirrored: return 2
        case .down: return 3
        case .downMirrored: return 4
        case .leftMirrored: return 5
        case .right: return 6
        case .rightMirrored: return 7
        case .left: return 8
        @unknown default: return 1
        }
    }

    // MARK: - 화면·공유용 읽기

    /// 크기를 줄이고 사진 방향(세로/가로)을 바로잡아 읽는다. 결과는 scale 1, 방향 .up.
    static func load(_ file: String, maxPixel: CGFloat) -> UIImage? {
        let opts = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let src = CGImageSourceCreateWithURL(url(file) as CFURL, opts) else { return nil }
        let o: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, o as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }
}

/// 그리드 썸네일·상세 사진 캐시
final class ImageCache: @unchecked Sendable {
    static let shared = ImageCache()
    private let cache = NSCache<NSString, UIImage>()

    init() { cache.countLimit = 400 }

    static func key(_ file: String, _ size: CGFloat) -> NSString { "\(file)@\(Int(size))" as NSString }

    func get(_ file: String, _ size: CGFloat) -> UIImage? { cache.object(forKey: Self.key(file, size)) }
    func set(_ img: UIImage, _ file: String, _ size: CGFloat) { cache.setObject(img, forKey: Self.key(file, size)) }

    func fetch(_ file: String, _ size: CGFloat) async -> UIImage? {
        if let c = get(file, size) { return c }
        let img = await runInBackground { PhotoStore.load(file, maxPixel: size) }
        if let img = img { set(img, file, size) }
        return img
    }
}
