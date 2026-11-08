import SwiftUI

struct ShareTarget: Identifiable {
    let entry: LogEntry
    let note: String
    var id: String { entry.id }
}

enum ShareChoice {
    case edit, original, copy
}

/// 공유하기: 크기 조절해서 공유 / 원본 그대로 공유 / 기록 복사
struct ShareFlow: ViewModifier {
    @Binding var target: ShareTarget?
    @EnvironmentObject var model: AppModel
    @State private var pending: (ShareChoice, ShareTarget)?
    @State private var editing: ShareTarget?

    init(target: Binding<ShareTarget?>) {
        _target = target
    }

    func body(content: Content) -> some View {
        content
            .sheet(item: $target, onDismiss: runPending) { t in
                ShareOptionsSheet(
                    onChoose: { c in
                        pending = (c, t)
                        target = nil
                    },
                    onClose: { target = nil }
                )
                .presentationDetents([.height(330), .medium])
            }
            .fullScreenCover(item: $editing) { t in
                ShareEditor(entry: t.entry, note: t.note).environmentObject(model)
            }
    }

    /// 선택지는 시트가 완전히 닫힌 뒤 실행한다(그래야 공유 시트·편집 화면을 띄울 수 있다).
    private func runPending() {
        guard let p = pending else { return }
        pending = nil
        let (choice, t) = p
        switch choice {
        case .edit:
            editing = t
        case .original:
            Sharing.send(file: PhotoStore.url(t.entry.file), text: Sharing.text(t.entry, note: t.note))
        case .copy:
            Sharing.copy(Sharing.text(t.entry, note: t.note))
            model.show("메모를 복사했습니다")
        }
    }
}

private struct ShareOptionsSheet: View {
    let onChoose: (ShareChoice) -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("공유하기").font(.system(size: 20, weight: .semibold))
                Spacer()
                Button("닫기", action: onClose).foregroundColor(Palette.onSurface)
            }
            .padding(.bottom, 10)
            option("크기 조절해서 공유", "비율(1:1 · 4:5 · 9:16)과 보낼 부분을 고르고 기록과 함께", .edit)
            option("원본 그대로 공유", "사진 파일 + 기록 글 · 사진 속 위치 정보도 함께 전달됩니다", .original)
            option("기록 복사", "기록 · 날짜 · 지도 링크", .copy)
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Palette.background.ignoresSafeArea())
    }

    private func option(_ title: String, _ sub: String, _ c: ShareChoice) -> some View {
        Button {
            onChoose(c)
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 16, weight: .medium)).foregroundColor(Palette.onSurface)
                Text(sub).font(.system(size: 12)).foregroundColor(Palette.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 8)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
