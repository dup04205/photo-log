import Foundation
import ZIPFoundation

struct ExportFile: Identifiable, Sendable {
    let id = UUID()
    let url: URL
    let entries: Int
    let photos: Int
}

/**
 * 백업: 사진과 기록을 zip 파일 하나로 내보내고 다시 불러온다.
 * zip 구성: entries.json(기록 목록, 사진은 photos/파일이름으로 가리킴) + photos/ 폴더.
 * 안드로이드판과 같은 형식이라 서로의 백업을 불러올 수 있다.
 */
enum Backup {
    struct ImportResult: Sendable {
        let added: [LogEntry]
        let skipped: Int
    }

    struct Failure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    private static let entriesName = "entries.json"
    private static let photosPrefix = "photos/"

    static func makeArchive(_ entries: [LogEntry]) throws -> ExportFile {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent("backup", isDirectory: true)
        try? fm.removeItem(at: dir)
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        let zipURL = dir.appendingPathComponent("PHOTO-LOG-백업-\(Dates.format(Date(), "yyyyMMdd")).zip")

        let archive: Archive
        do {
            archive = try Archive(url: zipURL, accessMode: .create, pathEncoding: nil)
        } catch {
            throw Failure(message: "백업 파일을 만들 수 없습니다")
        }

        var arr: [[String: Any]] = []
        var photos = 0
        for e in entries {
            let src = PhotoStore.url(e.file)
            var name = ""
            if !e.file.isEmpty, fm.fileExists(atPath: src.path) {
                name = photosPrefix + e.file
                // 안드로이드 ZipInputStream이 읽을 수 있도록 모두 deflate로 넣는다
                try archive.addEntry(with: name, fileURL: src, compressionMethod: .deflate)
                photos += 1
            }
            arr.append(e.json(uri: name))
        }
        let json = try JSONSerialization.data(withJSONObject: arr, options: [.prettyPrinted, .withoutEscapingSlashes])
        let jsonURL = dir.appendingPathComponent(entriesName)
        try json.write(to: jsonURL)
        try archive.addEntry(with: entriesName, fileURL: jsonURL, compressionMethod: .deflate)
        try? fm.removeItem(at: jsonURL)
        return ExportFile(url: zipURL, entries: entries.count, photos: photos)
    }

    /// 이미 있는 기록(id가 같은 것)은 건너뛰고 새 기록만 더한다.
    static func restore(_ src: URL, existingIds: Set<String>) throws -> ImportResult {
        let fm = FileManager.default
        let archive: Archive
        do {
            archive = try Archive(url: src, accessMode: .read, pathEncoding: nil)
        } catch {
            throw Failure(message: "백업 파일을 열 수 없습니다")
        }

        var copied: [String: URL] = [:]
        var json: Data?
        do {
            for ze in archive {
                guard ze.type == .file else { continue }
                if ze.path == entriesName {
                    var d = Data()
                    _ = try archive.extract(ze) { d.append($0) }
                    json = d
                } else if ze.path.hasPrefix(photosPrefix) {
                    // 경로 조작을 막기 위해 파일 이름만 쓴다
                    let base = (ze.path as NSString).lastPathComponent
                    guard !base.isEmpty, !base.hasPrefix(".") else { continue }
                    let e = (base as NSString).pathExtension
                    let dst = PhotoStore.newFile(ext: e.isEmpty ? "jpg" : e, preferred: base)
                    _ = try archive.extract(ze, to: dst)
                    copied[ze.path] = dst
                }
            }
        } catch {
            copied.values.forEach { try? fm.removeItem(at: $0) }
            throw Failure(message: "백업 파일을 읽지 못했습니다")
        }

        guard let data = json,
              let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]]
        else {
            copied.values.forEach { try? fm.removeItem(at: $0) }
            throw Failure(message: "PHOTO-LOG 백업 파일이 아닙니다")
        }

        var added: [LogEntry] = []
        var used = Set<String>()
        var skipped = 0
        for o in arr {
            let uri = o["uri"] as? String ?? ""
            guard let id = o["id"] as? String, !existingIds.contains(id),
                  let photo = copied[uri],
                  let e = LogEntry.from(json: o, file: photo.lastPathComponent)
            else {
                skipped += 1
                continue
            }
            used.insert(uri)
            added.append(e)
        }
        // 건너뛴 기록의 사진 복사본은 지운다
        for (k, u) in copied where !used.contains(k) { try? fm.removeItem(at: u) }
        return ImportResult(added: added, skipped: skipped)
    }
}
