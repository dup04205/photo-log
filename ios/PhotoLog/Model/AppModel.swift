import SwiftUI
import PhotosUI

struct Toast: Identifiable, Equatable {
    let id = UUID()
    let text: String
}

@MainActor
final class AppModel: ObservableObject {
    @Published var entries: [LogEntry] = EntryRepo.load()
    @Published var busy = false
    @Published var toast: Toast?
    @Published var path: [String] = []
    private var toastTask: Task<Void, Never>?

    /// 날짜 최신순, 같은 날은 나중에 넣은 것이 먼저
    var byDay: [DayGroup] {
        let sorted = entries.sorted { $0.date != $1.date ? $0.date > $1.date : $0.created > $1.created }
        var out: [DayGroup] = []
        for e in sorted {
            if out.last?.day == e.date {
                out[out.count - 1].items.append(e)
            } else {
                out.append(DayGroup(day: e.date, items: [e]))
            }
        }
        return out
    }

    func entry(_ id: String) -> LogEntry? { entries.first { $0.id == id } }

    private func persist() { EntryRepo.save(entries) }

    func show(_ text: String, long: Bool = false) {
        toast = Toast(text: text)
        toastTask?.cancel()
        toastTask = Task {
            try? await Task.sleep(nanoseconds: long ? 3_500_000_000 : 2_000_000_000)
            if !Task.isCancelled { toast = nil }
        }
    }

    private func addEntry(file: String, info: PhotoInfo, open: Bool) {
        let now = Dates.nowMillis
        let e = LogEntry(
            id: "p\(now)_\(entries.count)", file: file, date: info.date, note: "",
            lat: info.lat, lon: info.lon, created: now
        )
        entries.append(e)
        persist()
        if open {
            path = [e.id]
            show(e.lat != nil ? "저장하고 촬영 위치를 기록했습니다" : "저장했습니다 (위치 정보 없음)")
        }
    }

    /// 사진 앱에서 고른 사진(여러 장 가능)을 앱 전용 공간으로 복사한다.
    func importItems(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        busy = true
        Task {
            var ok: [(String, PhotoInfo)] = []
            for item in items {
                guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
                let (aDate, aLoc) = PhotoStore.assetInfo(item.itemIdentifier)
                let r = await runInBackground { PhotoStore.importData(data, assetDate: aDate, assetLocation: aLoc) }
                if let r = r { ok.append(r) }
            }
            busy = false
            if ok.count == 1 && items.count == 1 {
                addEntry(file: ok[0].0, info: ok[0].1, open: true)
                return
            }
            ok.forEach { addEntry(file: $0.0, info: $0.1, open: false) }
            let failed = items.count - ok.count
            let withPlace = ok.filter { $0.1.lat != nil }.count
            let msg: String
            if ok.isEmpty {
                msg = "사진을 가져오지 못했습니다"
            } else if failed > 0 {
                msg = "\(ok.count)장을 가져왔습니다 (위치 \(withPlace)장, 실패 \(failed)장)"
            } else {
                msg = "\(ok.count)장을 가져왔습니다 (위치 정보 \(withPlace)장)"
            }
            show(msg, long: true)
        }
    }

    /// 카메라 촬영 결과를 저장한다.
    func capture(image: UIImage, metadata: [String: Any]) {
        busy = true
        let loc = LocationProvider.shared.recent()
        Task {
            let r = await runInBackground { PhotoStore.saveCapture(image: image, metadata: metadata, location: loc) }
            busy = false
            guard let r = r else {
                show("사진을 저장하지 못했습니다")
                return
            }
            addEntry(file: r.0, info: r.1, open: true)
        }
    }

    func delete(_ e: LogEntry) {
        PhotoStore.delete(e.file)
        entries.removeAll { $0.id == e.id }
        persist()
        path.removeAll { $0 == e.id }
        show("삭제했습니다")
    }

    func saveNote(id: String, note: String) {
        guard let i = entries.firstIndex(where: { $0.id == id }) else { return }
        entries[i].note = note
        persist()
    }

    // MARK: - 백업

    func exportBackup() {
        busy = true
        let list = entries
        Task {
            let r: Result<ExportFile, Error> = await runInBackground { Result { try Backup.makeArchive(list) } }
            busy = false
            switch r {
            case .success(let f):
                ExportPresenter.present(f.url) { [weak self] saved in
                    try? FileManager.default.removeItem(at: f.url)
                    if saved { self?.show("백업했습니다 (기록 \(f.entries)개, 사진 \(f.photos)장)", long: true) }
                }
            case .failure(let err):
                show("백업하지 못했습니다: \(err.localizedDescription)", long: true)
            }
        }
    }

    func importBackup(_ url: URL) {
        busy = true
        let ids = Set(entries.map(\.id))
        Task {
            let r: Result<Backup.ImportResult, Error> = await runInBackground {
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                return Result { try Backup.restore(url, existingIds: ids) }
            }
            busy = false
            switch r {
            case .success(let res):
                entries.append(contentsOf: res.added)
                persist()
                show(res.skipped > 0
                     ? "\(res.added.count)개를 불러왔습니다 (이미 있는 \(res.skipped)개는 건너뜀)"
                     : "\(res.added.count)개를 불러왔습니다", long: true)
            case .failure(let err):
                show("불러오지 못했습니다: \(err.localizedDescription)", long: true)
            }
        }
    }
}
