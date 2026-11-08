import SwiftUI

private struct RatioOption {
    let label: String
    let value: CGFloat?
}

private let ratios: [RatioOption] = [
    RatioOption(label: "원본", value: nil),
    RatioOption(label: "1:1", value: 1),
    RatioOption(label: "4:5", value: 4.0 / 5.0),
    RatioOption(label: "9:16", value: 9.0 / 16.0),
]

/// 공유 형식
enum ShareStyle: CaseIterable, Identifiable {
    case photo, overlay, card
    var id: Self { self }
    var label: String {
        switch self {
        case .photo: return "사진만"
        case .overlay: return "사진 위에 기록"
        case .card: return "사진 + 기록 카드"
        }
    }
}

/**
 * 공유할 사진 크기 조절 화면.
 * 비율과 형식을 고르고, 두 손가락으로 확대·끌어서 위치를 맞춘 뒤 기록과 함께 공유한다.
 */
struct ShareEditor: View {
    let entry: LogEntry
    let note: String
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var sizeClass

    @State private var image: UIImage?
    @State private var ratioIdx = 2 // 기본 4:5
    @State private var zoom: CGFloat = 1
    @State private var offset: CGSize = .zero // 사진 틀 가로 폭을 1로 둔 이동량
    @State private var style: ShareStyle = .photo
    @State private var sendText: Bool
    @State private var stamp = true
    @State private var working = false

    init(entry: LogEntry, note: String, initialRatio: Int = 2, initialStyle: ShareStyle = .photo) {
        self.entry = entry
        self.note = note
        _sendText = State(initialValue: !note.isBlank)
        _ratioIdx = State(initialValue: min(max(initialRatio, 0), ratios.count - 1))
        _style = State(initialValue: note.isBlank ? .photo : initialStyle)
    }

    private var hasNote: Bool { !note.isBlank }

    private func ratio(_ img: UIImage?) -> CGFloat {
        if let v = ratios[ratioIdx].value { return v }
        if let img = img, img.size.height > 0 { return img.size.width / img.size.height }
        return 1
    }

    private func reset() {
        zoom = 1
        offset = .zero
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("공유할 사진 크기").font(.system(size: 17, weight: .medium))
                Spacer()
                Button("닫기") { dismiss() }.foregroundColor(Palette.onSurface)
            }

            GeometryReader { geo in
                preview(geo.size).frame(width: geo.size.width, height: geo.size.height)
            }

            Text("두 손가락으로 확대하고, 끌어서 보낼 부분을 맞춥니다")
                .font(.system(size: 12))
                .foregroundColor(Palette.onSurfaceVariant)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ratios.indices, id: \.self) { i in
                        Chip(label: ratios[i].label, selected: i == ratioIdx) {
                            ratioIdx = i
                            reset()
                        }
                    }
                }
                .padding(.vertical, 1)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ShareStyle.allCases) { st in
                        Chip(label: st.label, selected: st == style, enabled: st == .photo || hasNote) {
                            if st != style {
                                style = st
                                reset()
                            }
                        }
                    }
                }
                .padding(.vertical, 1)
            }
            SwitchRow(
                title: "기록을 글로 함께 보내기",
                sub: "카카오톡·메시지에는 글로 붙고, 인스타그램용으로 클립보드에도 복사합니다",
                isOn: Binding(get: { sendText && hasNote }, set: { sendText = $0 })
            )
            .disabled(!hasNote)
            SwitchRow(title: "필름카메라 날짜 넣기", sub: "사진의 우측 하단에 날짜를 넣습니다", isOn: $stamp)

            Button(action: share) {
                Text(working ? "만드는 중…" : "공유").frame(maxWidth: .infinity)
            }
            .buttonStyle(FilledButtonStyle())
            .disabled(image == nil || working)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        // iPad에서는 가운데에 적당한 폭으로
        .frame(maxWidth: sizeClass == .regular ? 640 : .infinity)
        .frame(maxWidth: .infinity)
        .background(Palette.background.ignoresSafeArea())
        .task {
            let f = entry.file
            image = await runInBackground { PhotoStore.load(f, maxPixel: 2048) }
        }
        .toastHost()
    }

    @ViewBuilder
    private func preview(_ space: CGSize) -> some View {
        let r = ratio(image)
        if style != .card {
            let w = min(space.width, space.height * r)
            PhotoFrame(
                image: image, width: w, ratio: r, zoom: $zoom, offset: $offset,
                date: entry.date, stamp: stamp,
                overlayNote: hasNote && style == .overlay ? note : nil
            )
        } else {
            // 카드 결과물과 같은 비율: 가로 1080 중 여백 54, 사진 폭 972
            let lines = hasNote ? min(ShareImage.lineCount(note), ShareImage.maxNoteLines) : 0
            let unit: CGFloat = 1 / 1080
            let padF = 54 * unit
            // 한 줄 높이 = 글자 크기 37.8 × 줄 높이 1.2 + 줄 간격 15.1 (iOS 글꼴 기준)
            let textF = lines > 0 ? (54 + CGFloat(lines) * (37.8 * 1.2 + 15.1)) * unit : 0
            let footF = (65 + 26 + 54) * unit
            // 카드 가로 cw 일 때 높이 = cw * (pad + (0.9/ratio) + text + foot)
            let hPerW = padF + 0.9 / r + textF + footF
            let cw = min(space.width, space.height / hPerW)
            let pw = cw * 0.9
            VStack(alignment: .leading, spacing: 0) {
                PhotoFrame(
                    image: image, width: pw, ratio: r, zoom: $zoom, offset: $offset,
                    date: entry.date, stamp: stamp, overlayNote: nil
                )
                if lines > 0 {
                    Text(note)
                        .font(.system(size: cw * 0.035))
                        .lineSpacing(cw * 0.014)
                        .foregroundColor(Color(UIColor(hex: 0x111111)))
                        .lineLimit(ShareImage.maxNoteLines)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, cw * 0.05)
                }
                HStack(alignment: .bottom, spacing: 4) {
                    Text(footText)
                        .font(.system(size: cw * 0.024, design: .monospaced))
                        .foregroundColor(Color(UIColor(hex: 0x8A8F98)))
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text("PHOTO-LOG")
                        .font(.custom(AppFont.titleName, fixedSize: cw * 0.029))
                        .foregroundColor(Color(UIColor(hex: 0x111111)))
                }
                .padding(.top, cw * 0.05)
            }
            .padding(cw * 0.05)
            .frame(width: cw)
            .background(Color(UIColor(hex: 0xF7F7F5)))
        }
    }

    private var footText: String {
        var parts = [entry.dottedDate]
        if let lat = entry.lat, let lon = entry.lon { parts.append(String(format: "%.4f, %.4f", lat, lon)) }
        return parts.joined(separator: "   ·   ")
    }

    private func share() {
        guard let img = image else { return }
        let r = ratio(img)
        let crop = Crop.rect(img.size, r, zoom, Crop.clamp(offset, img.size, r, zoom))
        let chosen = style
        let e = entry
        let n = note
        let st = stamp
        let has = hasNote
        working = true
        Task {
            let url: URL? = await runInBackground {
                switch chosen {
                case .card where has:
                    return ShareImage.renderCard(img, crop: crop, ratio: r, entry: e, note: n, stamp: st)
                case .overlay where has:
                    return ShareImage.render(img, crop: crop, ratio: r, entry: e, overlayNote: n, stamp: st)
                default:
                    return ShareImage.render(img, crop: crop, ratio: r, entry: e, overlayNote: "", stamp: st)
                }
            }
            working = false
            guard let url = url else {
                model.show("이미지를 만들지 못했습니다")
                return
            }
            let text = sendText && has ? Sharing.text(e, note: n) : nil
            if let text = text { Sharing.copy(text) }
            Sharing.send(file: url, text: text) { dismiss() }
        }
    }
}

/// 확대·이동할 수 있는 사진 틀. 날짜는 항상 우측 하단에 고정, 사진 위 기록은 날짜 위쪽에 놓는다.
private struct PhotoFrame: View {
    let image: UIImage?
    let width: CGFloat
    let ratio: CGFloat
    @Binding var zoom: CGFloat
    @Binding var offset: CGSize
    let date: String
    let stamp: Bool
    let overlayNote: String?

    @State private var dragStart: CGSize?
    @State private var zoomStart: CGFloat?

    var body: some View {
        let h = width / ratio
        Color.black
            .overlay { imageLayer }
            .overlay(alignment: .bottom) {
                if let n = overlayNote { noteLayer(n) }
            }
            .overlay(alignment: .bottomTrailing) {
                if stamp {
                    FilmDateStamp(date: date, size: width * 0.03)
                        .padding(.trailing, width * 0.045)
                        .padding(.bottom, width * 0.025)
                }
            }
            .frame(width: width, height: h)
            .clipped()
            .contentShape(Rectangle())
            .gesture(drag.simultaneously(with: magnify))
    }

    @ViewBuilder
    private var imageLayer: some View {
        if let image = image {
            let s = Crop.coverScale(image.size, ratio) * zoom
            let o = Crop.clamp(offset, image.size, ratio, zoom)
            Image(uiImage: image)
                .resizable()
                .interpolation(.high)
                .frame(width: image.size.width * s * width, height: image.size.height * s * width)
                .offset(x: o.width * width, y: o.height * width)
        } else {
            ProgressView().tint(.white)
        }
    }

    private func noteLayer(_ n: String) -> some View {
        let bottomGap = stamp ? width * (0.035 + 0.03 + 0.025) : width * 0.05
        return Text(n)
            .font(.system(size: width * 0.035))
            .lineSpacing(width * 0.014)
            .foregroundColor(.white)
            .lineLimit(ShareImage.maxNoteLines)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, width * 0.05)
            .padding(.top, width * 0.05)
            .padding(.bottom, bottomGap)
            .background(LinearGradient(colors: [.clear, Color.black.opacity(0.7)], startPoint: .top, endPoint: .bottom))
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard let image = image, width > 0 else { return }
                let start = dragStart ?? offset
                if dragStart == nil { dragStart = offset }
                let n = CGSize(
                    width: start.width + v.translation.width / width,
                    height: start.height + v.translation.height / width
                )
                offset = Crop.clamp(n, image.size, ratio, zoom)
            }
            .onEnded { _ in dragStart = nil }
    }

    private var magnify: some Gesture {
        MagnificationGesture()
            .onChanged { m in
                guard let image = image else { return }
                let start = zoomStart ?? zoom
                if zoomStart == nil { zoomStart = zoom }
                zoom = min(max(start * m, 1), 5)
                offset = Crop.clamp(offset, image.size, ratio, zoom)
            }
            .onEnded { _ in zoomStart = nil }
    }
}

/// 사진 틀 가로를 1, 세로를 1/ratio로 둔 좌표계에서 확대·이동·자르기를 계산한다.
enum Crop {
    static func coverScale(_ img: CGSize, _ ratio: CGFloat) -> CGFloat {
        guard img.width > 0, img.height > 0 else { return 1 }
        return max(1 / img.width, (1 / ratio) / img.height)
    }

    /// 사진이 항상 틀을 빈틈없이 덮도록 이동 범위를 제한한다.
    static func clamp(_ o: CGSize, _ img: CGSize, _ ratio: CGFloat, _ zoom: CGFloat) -> CGSize {
        guard img.width > 0, img.height > 0 else { return .zero }
        let s = coverScale(img, ratio) * zoom
        let mx = max(0, (img.width * s - 1) / 2)
        let my = max(0, (img.height * s - 1 / ratio) / 2)
        return CGSize(width: min(max(o.width, -mx), mx), height: min(max(o.height, -my), my))
    }

    /// 틀에 보이는 부분을 원본 사진 픽셀 좌표로 바꾼다.
    static func rect(_ img: CGSize, _ ratio: CGFloat, _ zoom: CGFloat, _ o: CGSize) -> CGRect {
        let s = coverScale(img, ratio) * zoom
        let cx = img.width / 2 - o.width / s
        let cy = img.height / 2 - o.height / s
        let hw = 1 / (2 * s)
        let hh = (1 / ratio) / (2 * s)
        return CGRect(x: cx - hw, y: cy - hh, width: 2 * hw, height: 2 * hh)
    }
}
