import SwiftUI
import UIKit

/// 맨 위 화면에서 UIKit 화면(공유 시트·파일 저장)을 띄운다.
@MainActor
enum Presenter {
    static func top() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        let window = scene?.windows.first { $0.isKeyWindow } ?? scene?.windows.first
        var vc = window?.rootViewController
        while let p = vc?.presentedViewController, !p.isBeingDismissed { vc = p }
        return vc
    }

    static func present(_ c: UIViewController) {
        guard let top = top() else { return }
        if let pop = c.popoverPresentationController {
            pop.sourceView = top.view
            pop.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.maxY - 80, width: 1, height: 1)
            pop.permittedArrowDirections = []
        }
        top.present(c, animated: true)
    }
}

/// 백업 zip을 파일 앱(iCloud Drive·Google 드라이브 등)에 저장하게 한다.
@MainActor
final class ExportPresenter: NSObject, UIDocumentPickerDelegate {
    private static var current: ExportPresenter?
    private let done: (Bool) -> Void

    private init(done: @escaping (Bool) -> Void) { self.done = done }

    static func present(_ url: URL, done: @escaping (Bool) -> Void) {
        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        let d = ExportPresenter(done: done)
        current = d
        picker.delegate = d
        Presenter.present(picker)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        done(true)
        Self.current = nil
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        done(false)
        Self.current = nil
    }
}

struct MapLink: Identifiable {
    let name: String
    let url: URL
    var id: String { name }
}

enum Sharing {
    private static let placeName = "촬영 위치"

    static func kakaoLink(_ lat: Double, _ lon: Double) -> String {
        let name = placeName.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? ""
        return "https://map.kakao.com/link/map/\(name),\(lat),\(lon)"
    }

    /// 기록 · 날짜 · 지도 링크
    static func text(_ e: LogEntry, note: String) -> String {
        var parts: [String] = []
        if !note.isBlank { parts.append(note) }
        parts.append(e.dottedDate)
        if let lat = e.lat, let lon = e.lon { parts.append(kakaoLink(lat, lon)) }
        return parts.joined(separator: "\n")
    }

    /// iOS 공유 시트를 연다. 인스타그램·카카오톡·메시지 등이 여기에 뜬다.
    @MainActor
    static func send(file: URL, text: String?, completion: (() -> Void)? = nil) {
        var items: [Any] = [file]
        if let text = text, !text.isEmpty { items.append(text) }
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        vc.completionWithItemsHandler = { _, _, _, _ in completion?() }
        Presenter.present(vc)
    }

    static func copy(_ t: String) {
        UIPasteboard.general.string = t
    }

    static func maps(_ lat: Double, _ lon: Double) -> [MapLink] {
        let q = placeName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        var out: [MapLink] = []
        if let u = URL(string: "https://maps.apple.com/?ll=\(lat),\(lon)&q=\(q)") { out.append(MapLink(name: "Apple 지도", url: u)) }
        if let u = URL(string: kakaoLink(lat, lon)) { out.append(MapLink(name: "카카오맵", url: u)) }
        if let u = URL(string: "https://www.google.com/maps/search/?api=1&query=\(lat),\(lon)") {
            out.append(MapLink(name: "Google 지도", url: u))
        }
        return out
    }
}
