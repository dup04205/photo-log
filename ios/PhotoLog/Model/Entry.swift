import Foundation

/// 사진 한 장과 그 기록. 사진 파일은 앱 전용 공간(Application Support/photos)에 있고, file이 그 파일 이름이다.
/// JSON 형식은 안드로이드판과 같다("uri": "photos/파일이름") — 백업 zip을 두 플랫폼이 서로 불러올 수 있다.
struct LogEntry: Identifiable, Equatable, Sendable {
    var id: String
    var file: String
    var date: String      // YYYY-MM-DD
    var note: String
    var lat: Double?
    var lon: Double?
    var created: Int64    // 밀리초

    var hasPlace: Bool { lat != nil && lon != nil }
    var dottedDate: String { date.replacingOccurrences(of: "-", with: ".") }

    func json(uri: String? = nil) -> [String: Any] {
        var o: [String: Any] = [
            "id": id,
            "uri": uri ?? "photos/\(file)",
            "date": date,
            "note": note,
            "created": NSNumber(value: created),
        ]
        if let lat = lat { o["lat"] = lat }
        if let lon = lon { o["lon"] = lon }
        return o
    }

    static func from(json o: [String: Any], file: String? = nil) -> LogEntry? {
        guard let id = o["id"] as? String, let date = o["date"] as? String else { return nil }
        let uri = o["uri"] as? String ?? ""
        return LogEntry(
            id: id,
            file: file ?? (uri as NSString).lastPathComponent,
            date: date,
            note: o["note"] as? String ?? "",
            lat: (o["lat"] as? NSNumber)?.doubleValue,
            lon: (o["lon"] as? NSNumber)?.doubleValue,
            created: (o["created"] as? NSNumber)?.int64Value ?? 0
        )
    }
}

struct DayGroup: Identifiable {
    let day: String
    var items: [LogEntry]
    var id: String { day }
}

/// 앱 전용 저장 위치. 사진 앱·파일 앱에서는 보이지 않는다.
enum Paths {
    static let root: URL = {
        let fm = FileManager.default
        var u = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        #if targetEnvironment(macCatalyst)
        // 맥에서는 Application Support가 모든 앱이 함께 쓰는 폴더라, 앱 전용 하위 폴더에 둔다
        u = u.appendingPathComponent("com.dup04205.photolog", isDirectory: true)
        #endif
        try? fm.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }()

    static let photos: URL = {
        let u = root.appendingPathComponent("photos", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }()

    static let entries = root.appendingPathComponent("entries.json")
}

/// 기록·위치·날짜는 entries.json 하나에 보관한다. 서버는 없다.
enum EntryRepo {
    static func load() -> [LogEntry] {
        guard let data = try? Data(contentsOf: Paths.entries),
              let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]]
        else { return [] }
        return arr.compactMap { LogEntry.from(json: $0) }
    }

    static func save(_ list: [LogEntry]) {
        guard let data = try? JSONSerialization.data(withJSONObject: list.map { $0.json() }) else { return }
        try? data.write(to: Paths.entries, options: .atomic)
    }
}

enum Dates {
    static func format(_ d: Date, _ pattern: String) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.calendar = Calendar(identifier: .gregorian)
        f.dateFormat = pattern
        return f.string(from: d)
    }

    static func today() -> String { format(Date(), "yyyy-MM-dd") }
    static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

/// 필름 카메라식 날짜 스탬프: '26 11 05
func stampOf(_ date: String) -> String {
    let p = date.split(separator: "-", omittingEmptySubsequences: false).map(String.init)
    guard p.count == 3 else { return date }
    return "'\(p[0].suffix(2)) \(p[1]) \(p[2])"
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var isBlank: Bool { trimmed.isEmpty }
}

/// 무거운 작업(사진 읽기·쓰기, zip)을 백그라운드에서 돌리고 결과를 기다린다.
func runInBackground<T>(_ work: @escaping () -> T) async -> T {
    await withCheckedContinuation { cont in
        DispatchQueue.global(qos: .userInitiated).async {
            cont.resume(returning: work())
        }
    }
}
