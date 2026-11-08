import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

/// 기본 모드는 흰색, 다크 모드는 검정. 강조색은 날짜 스탬프 주황 하나만 쓴다.
enum Palette {
    static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }

    static let background = dynamic(0xFFFFFF, 0x000000)
    static let onSurface = dynamic(0x111111, 0xF5F5F5)
    static let primary = onSurface
    static let onPrimary = dynamic(0xFFFFFF, 0x000000)
    static let surfaceVariant = dynamic(0xF1F1F1, 0x1C1C1C)
    static let onSurfaceVariant = dynamic(0x6E6E6E, 0x9A9A9A)
    static let outline = dynamic(0xBDBDBD, 0x555555)
    static let outlineVariant = dynamic(0xE6E6E6, 0x2A2A2A)
    static let error = dynamic(0xB3261E, 0xF2B8B5)
    static let stamp = Color(UIColor(hex: 0xE8752A))

    static let filmOrange = UIColor(hex: 0xFF7A1A)
    static let filmGlow = UIColor(hex: 0xFF5A00, alpha: 0.8)
}

/// 제목 Playfair Display Italic, 사이드바·날짜 고운바탕, 날짜 각인 DSEG7, 메모는 시스템 글꼴
enum AppFont {
    static let titleName = "PlayfairDisplay-SemiBoldItalic"
    static let indexName = "GowunBatang-Regular"
    static let indexBoldName = "GowunBatang-Bold"
    static let stampName = "DSEG7Classic-Italic"

    static func index(_ size: CGFloat, bold: Bool = false) -> Font {
        .custom(bold ? indexBoldName : indexName, fixedSize: size)
    }
}

struct Logo: View {
    var body: some View {
        Text("PHOTO-LOG")
            .font(.custom(AppFont.titleName, fixedSize: 23))
            .kerning(1)
            .foregroundColor(Palette.onSurface)
            .accessibilityAddTraits(.isHeader)
    }
}

/// 필름카메라 날짜 각인: '26 11 05 를 주황 7세그먼트 숫자 + 빛 번짐으로
struct FilmDateStamp: View {
    let date: String
    var size: CGFloat = 13

    var body: some View {
        Text(stampOf(date))
            .font(.custom(AppFont.stampName, fixedSize: size))
            .kerning(size * 0.06)
            .foregroundColor(Color(Palette.filmOrange))
            .shadow(color: Color(Palette.filmGlow), radius: size * 0.4)
            .fixedSize()
    }
}

struct FilledButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .medium))
            .padding(.horizontal, 22)
            .frame(minHeight: 42)
            .foregroundColor(enabled ? Palette.onPrimary : Palette.onSurfaceVariant)
            .background(enabled ? Palette.primary : Palette.surfaceVariant, in: Capsule())
            .opacity(configuration.isPressed ? 0.75 : 1)
    }
}

struct OutlinedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .medium))
            .padding(.horizontal, 18)
            .frame(minHeight: 42)
            .foregroundColor(Palette.onSurface)
            .overlay(Capsule().strokeBorder(Palette.outline, lineWidth: 1))
            .contentShape(Capsule())
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// 비율·형식 고르기 칩
struct Chip: View {
    let label: String
    let selected: Bool
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if selected { Image(systemName: "checkmark").font(.system(size: 11, weight: .semibold)) }
                Text(label).font(.system(size: 14))
            }
            .padding(.horizontal, 12)
            .frame(height: 32)
            .foregroundColor(enabled ? Palette.onSurface : Palette.onSurfaceVariant.opacity(0.5))
            .background(selected ? Palette.surfaceVariant : Color.clear, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(selected ? Color.clear : Palette.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// 스위치 한 줄(제목 + 설명 + 스위치). 공유 화면의 옵션에 쓴다.
struct SwitchRow: View {
    let title: String
    let sub: String?
    @Binding var isOn: Bool
    @Environment(\.isEnabled) private var enabled

    init(title: String, sub: String?, isOn: Binding<Bool>) {
        self.title = title
        self.sub = sub
        _isOn = isOn
    }

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15))
                    .foregroundColor(enabled ? Palette.onSurface : Palette.onSurfaceVariant)
                if let sub = sub {
                    Text(sub).font(.system(size: 12)).foregroundColor(Palette.onSurfaceVariant)
                }
            }
        }
        .tint(Palette.stamp)
        .padding(.vertical, 4)
    }
}

// MARK: - 토스트

struct ToastHost: ViewModifier {
    @EnvironmentObject var model: AppModel

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let t = model.toast {
                    Text(t.text)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color(white: 0.18).opacity(0.95), in: RoundedRectangle(cornerRadius: 18))
                        .padding(.horizontal, 24)
                        .padding(.bottom, 96)
                        .id(t.id)
                        .transition(.opacity)
                        .allowsHitTesting(false)
                }
            }
            .animation(.easeInOut(duration: 0.2), value: model.toast)
    }
}

extension View {
    func toastHost() -> some View { modifier(ToastHost()) }

    /// 삭제 확인
    func deleteAlert(_ item: Binding<LogEntry?>, onConfirm: @escaping (LogEntry) -> Void) -> some View {
        alert(
            "삭제",
            isPresented: Binding(get: { item.wrappedValue != nil }, set: { if !$0 { item.wrappedValue = nil } }),
            presenting: item.wrappedValue
        ) { e in
            Button("삭제", role: .destructive) { onConfirm(e) }
            Button("취소", role: .cancel) {}
        } message: { _ in
            Text("이 사진과 기록을 삭제합니다. 사진 파일도 함께 지워지며 되돌릴 수 없습니다.")
        }
    }
}

/// 앱 전용 사진을 크기에 맞춰 읽어 보여 준다(채우기).
struct PhotoImage: View {
    let file: String
    let maxPixel: CGFloat
    @State private var image: UIImage?

    init(file: String, maxPixel: CGFloat) {
        self.file = file
        self.maxPixel = maxPixel
        _image = State(initialValue: ImageCache.shared.get(file, maxPixel))
    }

    var body: some View {
        ZStack {
            if let image = image {
                Image(uiImage: image).resizable().scaledToFill()
            }
        }
        .task(id: file) {
            if image == nil { image = await ImageCache.shared.fetch(file, maxPixel) }
        }
    }
}
