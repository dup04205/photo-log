import UIKit

/**
 * 공유 이미지. 가로 1080px.
 * - render: 고른 비율로 자른 사진 (+ 사진 위 기록) (+ 필름 날짜)
 * - renderCard: 사진 + 아래 기록 + 날짜·PHOTO-LOG 바닥글 카드
 * 날짜 각인은 기록 유무와 상관없이 사진의 우측 하단에 고정한다.
 */
enum ShareImage {
    /// 사진과 기록을 합쳐 공유할 때 최대 줄 수
    static let maxNoteLines = 5
    static let W: CGFloat = 1080
    static let pad: CGFloat = W * 0.05            // 54px
    static let textW: CGFloat = W - 2 * pad       // 972px, 카드·사진 위 기록 모두 같은 폭

    /// 공유 이미지 기준으로 기록을 배치한다(줄 수 세기·그리기 공용).
    final class NoteLayout {
        let storage: NSTextStorage
        let manager = NSLayoutManager()
        let container: NSTextContainer

        init(_ text: String, color: UIColor, maxLines: Int = 0) {
            let ps = NSMutableParagraphStyle()
            ps.lineSpacing = ShareImage.W * 0.014
            storage = NSTextStorage(string: text, attributes: [
                .font: UIFont.systemFont(ofSize: ShareImage.W * 0.035),
                .foregroundColor: color,
                .paragraphStyle: ps,
            ])
            container = NSTextContainer(size: CGSize(width: ShareImage.textW, height: .greatestFiniteMagnitude))
            container.lineFragmentPadding = 0
            container.maximumNumberOfLines = maxLines
            container.lineBreakMode = maxLines > 0 ? .byTruncatingTail : .byWordWrapping
            manager.addTextContainer(container)
            storage.addLayoutManager(manager)
            manager.ensureLayout(for: container)
        }

        var lineCount: Int {
            var n = 0
            manager.enumerateLineFragments(forGlyphRange: manager.glyphRange(for: container)) { _, _, _, _, _ in
                n += 1
            }
            // 끝이 줄바꿈이면 빈 줄 하나가 더 생긴다
            if manager.extraLineFragmentTextContainer != nil { n += 1 }
            return n
        }

        var height: CGFloat { ceil(manager.usedRect(for: container).height) }

        func draw(at p: CGPoint) {
            let r = manager.glyphRange(for: container)
            manager.drawBackground(forGlyphRange: r, at: p)
            manager.drawGlyphs(forGlyphRange: r, at: p)
        }
    }

    /// 공유 이미지에 넣었을 때 몇 줄이 되는지(엔터 줄 + 자동 줄바꿈).
    static func lineCount(_ text: String) -> Int {
        text.isEmpty ? 0 : NoteLayout(text, color: .white).lineCount
    }

    /// 사진 영역의 우측 하단에 필름카메라 날짜를 찍는다.
    private static func drawStamp(_ date: String, right: CGFloat, bottom: CGFloat, photoW: CGFloat) {
        let size = photoW * 0.03
        let font = UIFont(name: AppFont.stampName, size: size) ?? .monospacedSystemFont(ofSize: size, weight: .regular)
        let shadow = NSShadow()
        shadow.shadowBlurRadius = size * 0.4
        shadow.shadowOffset = .zero
        shadow.shadowColor = Palette.filmGlow
        let s = NSAttributedString(string: stampOf(date), attributes: [
            .font: font,
            .foregroundColor: Palette.filmOrange,
            .kern: size * 0.06,
            .shadow: shadow,
        ])
        let baseline = bottom - photoW * 0.035
        s.draw(at: CGPoint(x: right - photoW * 0.045 - s.size().width, y: baseline - font.ascender))
    }

    /// 날짜 각인이 차지하는 아래쪽 높이(사진 위 기록이 날짜와 겹치지 않도록 띄우는 값)
    private static func stampRowHeight(_ photoW: CGFloat) -> CGFloat { photoW * (0.035 + 0.03 + 0.025) }

    private static func drawCrop(_ src: UIImage, _ crop: CGRect, _ dst: CGRect) {
        guard let cg = src.cgImage else { return }
        let bounds = CGRect(x: 0, y: 0, width: cg.width, height: cg.height)
        let r = crop.integral.intersection(bounds)
        guard !r.isNull, r.width > 0, r.height > 0, let part = cg.cropping(to: r) else { return }
        UIGraphicsGetCurrentContext()?.interpolationQuality = .high
        UIImage(cgImage: part).draw(in: dst)
    }

    private static func renderer(_ size: CGSize) -> UIGraphicsImageRenderer {
        let f = UIGraphicsImageRendererFormat()
        f.scale = 1
        f.opaque = true
        return UIGraphicsImageRenderer(size: size, format: f)
    }

    static func render(
        _ src: UIImage, crop: CGRect, ratio: CGFloat, entry: LogEntry, overlayNote: String, stamp: Bool
    ) -> URL? {
        let w = W
        let h = (w / ratio).rounded()
        let img = renderer(CGSize(width: w, height: h)).image { ctx in
            let c = ctx.cgContext
            c.setFillColor(UIColor.black.cgColor)
            c.fill(CGRect(x: 0, y: 0, width: w, height: h))
            drawCrop(src, crop, CGRect(x: 0, y: 0, width: w, height: h))

            if !overlayNote.isBlank {
                let layout = NoteLayout(overlayNote, color: .white, maxLines: maxNoteLines)
                let bottomGap = stamp ? stampRowHeight(w) : pad
                let textTop = h - bottomGap - layout.height
                let shadeTop = max(0, textTop - pad)
                let colors = [
                    UIColor(red: 0, green: 0, blue: 0, alpha: 0).cgColor,
                    UIColor(red: 0, green: 0, blue: 0, alpha: 0.7).cgColor,
                ] as CFArray
                if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
                    c.saveGState()
                    c.clip(to: CGRect(x: 0, y: shadeTop, width: w, height: h - shadeTop))
                    c.drawLinearGradient(g, start: CGPoint(x: 0, y: shadeTop), end: CGPoint(x: 0, y: h), options: [])
                    c.restoreGState()
                }
                layout.draw(at: CGPoint(x: pad, y: textTop))
            }
            if stamp { drawStamp(entry.date, right: w, bottom: h, photoW: w) }
        }
        return save(img, entry)
    }

    static func renderCard(
        _ src: UIImage, crop: CGRect, ratio: CGFloat, entry: LogEntry, note: String, stamp: Bool
    ) -> URL? {
        let w = W
        let pw = textW
        let ph = pw / ratio
        let layout = note.isBlank ? nil : NoteLayout(note, color: UIColor(hex: 0x111111), maxLines: maxNoteLines)
        let textGap = W * 0.05
        let footerGap = W * 0.06
        let footerSize = W * 0.024
        let textH = layout.map { textGap + $0.height } ?? 0
        let h = (pad + ph + textH + footerGap + footerSize + pad).rounded()

        let img = renderer(CGSize(width: w, height: h)).image { ctx in
            let c = ctx.cgContext
            c.setFillColor(UIColor(hex: 0xF7F7F5).cgColor)
            c.fill(CGRect(x: 0, y: 0, width: w, height: h))
            let photo = CGRect(x: pad, y: pad, width: pw, height: ph)
            drawCrop(src, crop, photo)
            if stamp { drawStamp(entry.date, right: photo.maxX, bottom: photo.maxY, photoW: pw) }

            layout?.draw(at: CGPoint(x: pad, y: photo.maxY + textGap))

            let baseline = h - pad
            var parts = [entry.dottedDate]
            if let lat = entry.lat, let lon = entry.lon { parts.append(String(format: "%.4f, %.4f", lat, lon)) }
            let footFont = UIFont.monospacedSystemFont(ofSize: footerSize, weight: .regular)
            NSAttributedString(string: parts.joined(separator: "   ·   "), attributes: [
                .font: footFont,
                .foregroundColor: UIColor(hex: 0x8A8F98),
            ]).draw(at: CGPoint(x: pad, y: baseline - footFont.ascender))

            let brandFont = UIFont(name: AppFont.titleName, size: footerSize * 1.2) ?? .italicSystemFont(ofSize: footerSize * 1.2)
            let brand = NSAttributedString(string: "PHOTO-LOG", attributes: [
                .font: brandFont,
                .foregroundColor: UIColor(hex: 0x111111),
            ])
            brand.draw(at: CGPoint(x: w - pad - brand.size().width, y: baseline - brandFont.ascender))
        }
        return save(img, entry)
    }

    private static func save(_ img: UIImage, _ e: LogEntry) -> URL? {
        guard let data = img.jpegData(compressionQuality: 0.92) else { return nil }
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent("share", isDirectory: true)
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        let u = dir.appendingPathComponent("photolog_\(e.date.replacingOccurrences(of: "-", with: ""))_\(Dates.nowMillis).jpg")
        do {
            try data.write(to: u)
            return u
        } catch {
            return nil
        }
    }
}
