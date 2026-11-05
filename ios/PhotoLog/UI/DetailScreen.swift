import SwiftUI

/* ① 상세 화면: 사진 · 위치 · 기록 · 저장 · 공유 · ⋯ 삭제 */
struct DetailScreen: View {
    let id: String
    @EnvironmentObject var model: AppModel

    var body: some View {
        if let entry = model.entry(id) {
            DetailContent(entry: entry)
        } else {
            Palette.background.ignoresSafeArea()
        }
    }
}

private struct DetailContent: View {
    let entry: LogEntry
    @EnvironmentObject var model: AppModel
    @State private var note: String
    @State private var lastValid: String
    @State private var image: UIImage?
    @State private var deleteFor: LogEntry?
    @State private var shareTarget: ShareTarget?
    @FocusState private var noteFocused: Bool

    init(entry: LogEntry) {
        self.entry = entry
        _note = State(initialValue: entry.note)
        _lastValid = State(initialValue: entry.note)
    }

    var body: some View {
        let lines = ShareImage.lineCount(note)
        let trimmed = note.trimmed
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    photo
                    PlaceRow(entry: entry)
                    noteField(lines).id("note")
                    HStack(spacing: 8) {
                        Spacer()
                        Button {
                            noteFocused = false
                            shareTarget = ShareTarget(entry: entry, note: trimmed)
                        } label: {
                            Label("공유", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(OutlinedButtonStyle())
                        Button("저장") {
                            model.saveNote(id: entry.id, note: trimmed)
                            note = trimmed
                            noteFocused = false
                            model.show("저장했습니다")
                        }
                        .buttonStyle(FilledButtonStyle())
                        .disabled(trimmed == entry.note)
                    }
                    .id("buttons")
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: noteFocused) { focused in
                // 키보드가 올라오면 기록 칸이 가려지지 않게 끌어올린다
                guard focused else { return }
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 350_000_000)
                    withAnimation { proxy.scrollTo("buttons", anchor: .bottom) }
                }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbarRole(.editor)
        .toolbarBackground(Palette.background, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .principal) { Logo() }
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button(role: .destructive) {
                        deleteFor = entry
                    } label: {
                        Label("삭제", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis").rotationEffect(.degrees(90))
                }
                .accessibilityLabel("설정")
            }
        }
        .task(id: entry.file) {
            image = await ImageCache.shared.fetch(entry.file, 2048)
        }
        .onChange(of: note) { new in
            // 공유 이미지 기준 5줄을 넘는 입력은 받지 않는다(지우는 것은 언제나 허용)
            let n = ShareImage.lineCount(new)
            if n <= ShareImage.maxNoteLines || n <= ShareImage.lineCount(lastValid) {
                lastValid = new
            } else {
                note = lastValid
            }
        }
        .deleteAlert($deleteFor) { model.delete($0) }
        .modifier(ShareFlow(target: $shareTarget))
    }

    private var photo: some View {
        Group {
            if let image = image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity, maxHeight: 460)
            } else {
                Color.black.frame(height: 300).overlay(ProgressView().tint(.white))
            }
        }
        .background(Color.black)
        .overlay(alignment: .bottomTrailing) {
            FilmDateStamp(date: entry.date).padding(.trailing, 14).padding(.bottom, 12)
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .accessibilityElement()
        .accessibilityLabel("\(entry.date) 사진")
    }

    private func noteField(_ lines: Int) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("기록")
                .font(.system(size: 12))
                .foregroundColor(noteFocused ? Palette.onSurface : Palette.onSurfaceVariant)
                .padding(.leading, 4)
            ZStack(alignment: .topLeading) {
                if note.isEmpty {
                    Text("이 사진에 남길 이야기를 적어 두세요. (최대 5줄)")
                        .font(.system(size: 16))
                        .foregroundColor(Palette.onSurfaceVariant.opacity(0.8))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 8)
                        .allowsHitTesting(false)
                }
                TextEditor(text: $note)
                    .font(.system(size: 16))
                    .lineSpacing(10)
                    .focused($noteFocused)
                    .scrollContentBackground(.hidden)
                    .frame(minHeight: 200)
            }
            .padding(8)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(noteFocused ? Palette.onSurface : Palette.outline, lineWidth: noteFocused ? 2 : 1)
            )
            Text("\(lines) / \(ShareImage.maxNoteLines)줄")
                .font(.system(size: 12))
                .foregroundColor(lines >= ShareImage.maxNoteLines ? Palette.stamp : Palette.onSurfaceVariant)
                .padding(.leading, 4)
        }
    }
}

private struct PlaceRow: View {
    let entry: LogEntry
    @State private var mapChoice = false

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "mappin.and.ellipse")
                .font(.system(size: 15))
                .foregroundColor(entry.hasPlace ? Palette.stamp : Palette.onSurfaceVariant)
            if let lat = entry.lat, let lon = entry.lon {
                Text(String(format: "%.5f, %.5f", lat, lon))
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundColor(Palette.onSurface)
                Spacer(minLength: 4)
                Button("지도에서 보기") { mapChoice = true }
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Palette.onSurface)
                    .confirmationDialog("지도에서 보기", isPresented: $mapChoice, titleVisibility: .visible) {
                        ForEach(Sharing.maps(lat, lon)) { m in
                            Button(m.name) { UIApplication.shared.open(m.url) }
                        }
                    }
            } else {
                Text("이 사진에는 위치 정보가 없습니다")
                    .font(.system(size: 13))
                    .foregroundColor(Palette.onSurfaceVariant)
                Spacer()
            }
        }
    }
}
